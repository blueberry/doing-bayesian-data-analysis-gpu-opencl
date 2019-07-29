;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch09.therapeutic-touch
  (:require [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float info]]
            [uncomplicate.fluokitten.core :refer [op]]
            [uncomplicate.neanderthal
             [core :refer [row native dot imax imin scal! col submatrix transfer]]
             [real :refer [entry entry!]]
             [native :refer [fv fge]]]
            [uncomplicate.bayadera
             [core :refer :all]
             [library :as library]
             [util :refer [bin-mapper hdi]]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix!]]]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def all-data (atom {}))
(def state (atom nil))

(def subjects 28)

(let [in-file (slurp (io/resource "dbda/ch09/therapeutic-touch-data.csv"))]
  (def params (fv (seq (reduce (fn [^ints acc [b c]]
                                 (let [c (* 2 (dec (int (bigint (subs c 1)))))]
                                   (aset acc c (inc (aget acc c) ))
                                   (aset acc (inc c) (+ (aget acc (inc c)) (int (read-string b))))
                                   acc))
                               (int-array 56)
                               (drop 1 (csv/read-csv in-file)))))))

(defn analysis []
  (with-default-bayadera
    (let [walker-count (* 256 44)]
      (with-release [touch-prior
                     (library/distribution-model [:beta :gamma
                                                  (slurp (io/resource "dbda/ch09/therapeutic-touch.cl"))]
                                                 {:name "touch" :params-size 4 :dimension (+ subjects 2)})
                     touch-likelihood
                     (library/likelihood-model [:binomial
                                                (slurp (io/resource "dbda/ch09/therapeutic-touch-lik.cl"))]
                                               {:name "touch"})
                     limits (fge 2 (+ subjects 2)
                                 (op (take (+ 2 (* subjects 2))
                                           (interleave (repeat 0) (repeat 1)))
                                     [0 30]))
                     prior (distribution touch-prior)
                     prior-dist (prior (fv 1 1 1.105125 1.105125))
                     post (distribution "touch" touch-likelihood prior-dist)
                     post-dist (post (fv (take (* subjects 2) params)))
                     post-sampler (sampler post-dist {:walkers walker-count :limits limits})]
        (println (time (mix! post-sampler {:refining 20})))
        (println (info post-sampler))
        (histogram! post-sampler 320)))))

(defn setup []
  (reset! state
          {:data @all-data
           :omega (plot2d (qa/current-applet) {:width 300 :height 300})
           :kappa-2 (plot2d (qa/current-applet) {:width 300 :height 300})
           :thetas (vec (repeatedly 28 (partial plot2d (qa/current-applet)
                                                {:width 180 :height 180})))}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (let [data @all-data]
      (q/background 0)
      (q/image (show (render-histogram (:omega @state) data subjects)) 0 0)
      (q/image (show (render-histogram (:kappa-2 @state) data (inc subjects))) 350 0)
      (dotimes [i 6]
        (dotimes [j 5]
          (let [index (+ (* i 5) j)]
            (when (< index 28)
              (q/image (show (render-histogram ((:thetas @state) index) data index))
                       (* j 200) (+ 320 (* i 200))))))))))

(defn display-sketch []
  (q/defsketch diagrams
    :renderer :p2d
    :size :fullscreen
    :display 2
    :setup setup
    :draw draw
    :middleware [pause-on-error]))

;; This is how to run it:
;; 1. Display empty window (preferrably spanning the screen)
#_(display-sketch)
;; 2. Run the analysis to populate the data that the plots draw
#_(reset! all-data (analysis))
;; It is awkward, but I was constrained by how quil and processing
;; manage display.

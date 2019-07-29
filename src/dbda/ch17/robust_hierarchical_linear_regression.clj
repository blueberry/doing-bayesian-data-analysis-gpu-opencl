;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch17.robust-hierarchical-linear-regression
  (:require [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float]]
            [uncomplicate.fluokitten.core :refer [op fmap]]
            [uncomplicate.neanderthal
             [core :refer [dim]]
             [real :refer [entry entry!]]
             [math :refer [sqrt]]
             [native :refer [fv fge]]]
            [uncomplicate.bayadera
             [core :refer :all]
             [library :as library]
             [util :refer [bin-mapper hdi]]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix! burn-in! pow-n acc-rate! run-sampler!]]]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def all-data (atom {}))
(def state (atom nil))

(defn read-data [in-file]
  (loop [c 0 data (drop 1 (csv/read-csv in-file)) hws (transient {})]
    (if data
      (let [[s h w] (first data)]
        (recur (inc c) (next data)
               (let [s (read-string s)
                     hw (get hws s (transient []))]
                 (assoc! hws s
                         (-> hw
                             (conj! (double (read-string h)))
                             (conj! (double (read-string w))))))))
      (let [persistent-hws (into (sorted-map) (fmap persistent! (persistent! hws)))
            subject-count (count persistent-hws)]
        (apply op [subject-count] (map (fn [[k v]] (op [(count v)] v)) persistent-hws))))))

(def params (fv (read-data (slurp (io/resource "dbda/ch17/hier-lin-regress-data.csv")))))

(defn analysis []
  (with-default-bayadera
    (with-release [rhlr-prior
                   (library/distribution-model [:gaussian :uniform :exponential :student-t
                                           (slurp (io/resource "dbda/ch17/robust-hierarchical-linear-regression.cl"))]
                                          {:name "rhlr" :mcmc-logpdf "rhlr_mcmc_logpdf"
                                           :params-size 103 :dimension 52})
                   rhlr-likelihood
                   (library/likelihood-model (slurp (io/resource "dbda/ch17/robust-hierarchical-linear-regression.cl"))
                                             {:name "rhlr"})
                   prior (distribution rhlr-prior)
                   prior-dist (prior (fv (op [4 0.01 1000] (take 100 (cycle [0 100 3 10])))))
                   post (distribution "rhlr" rhlr-likelihood prior-dist)
                   post-dist (post params)
                   post-sampler (sampler post-dist {:limits (fge 2 52 (op [2 20 0.001 100] (take 100 (interleave (repeat -100) (repeat 100) (repeat -3) (repeat 9)))))})]
      (println (time (mix! post-sampler {:dimension-power 0.2 :cooling-schedule (pow-n 4)})))
      (println (time (do (burn-in! post-sampler 3000) (acc-rate! post-sampler))))
      (println (time (run-sampler! post-sampler 64)))
      (time (histogram! post-sampler 1)))))

(defn setup []
  (reset! state
          {:data @all-data
           :nu (plot2d (qa/current-applet) {:width 300 :height 300})
           :sigma (plot2d (qa/current-applet) {:width 300 :height 300})
           :betas (vec (repeatedly 50 (partial plot2d (qa/current-applet)
                                               {:width 300 :height 300})))}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (let [data @all-data]
      (q/background 0)
      (q/image (show (render-histogram (:nu @state) data 0)) 0 0)
      (q/image (show (render-histogram (:sigma @state) data 1)) 350 0)
      (dotimes [i 4]
        (dotimes [j 3]
          (let [index (+ (* i 3) j)]
            (when (< index 12)
              (q/image (show (render-histogram ((:betas @state) index) data (+ index 2)))
                       (* j 320) (+ 320 (* i 320))))))))))

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

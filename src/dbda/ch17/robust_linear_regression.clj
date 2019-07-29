;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch17.robust-linear-regression
  (:require [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release info]]
            [uncomplicate.fluokitten.core :refer [op]]
            [uncomplicate.clojurecl.core :refer [finish!]]
            [uncomplicate.neanderthal
             [core :refer [dim]]
             [native :refer [fv fge]]]
            [uncomplicate.bayadera
             [core :refer :all]
             [library :as library]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix!]]]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def all-data (atom {}))
(def state (atom nil))

(defn read-data [in-file]
  (loop [c 0 data (drop 1 (csv/read-csv in-file)) hw (transient [])]
    (if data
      (let [[_ h w] (first data)]
        (recur (inc c) (next data)
               (-> hw
                   (conj! (double (read-string h)))
                   (conj! (double (read-string w))))))
      (op [c] (persistent! hw)))))

(def params-30 (fv (read-data (slurp (io/resource "dbda/ch17/ht-wt-data-30.csv")))))
(def params-300 (fv (read-data (slurp (io/resource "dbda/ch17/ht-wt-data-300.csv")))))

(defn analysis []
  (with-default-bayadera
    (with-release [rlr-prior
                   (library/distribution-model [:gaussian :uniform :exponential :student-t
                                                (slurp (io/resource "dbda/ch17/robust-linear-regression.cl"))]
                                               {:name "rlr" :mcmc-logpdf "rlr_mcmc_logpdf"
                                                :params-size 7 :dimension 4})
                   rlr-likelihood
                   (library/likelihood-model (slurp (io/resource "dbda/ch17/robust-linear-regression.cl"))
                                             {:name "rlr"})
                   prior (distribution rlr-prior)
                   prior-dist (prior (fv 10 -100 100 5 10 0.001 1000))
                   prior-sampler (sampler prior-dist {:walkers 22528 :limits (fge 2 4 [1 20 -400 100 0 20 0.01 100])})
                   post (distribution "rlr" rlr-likelihood prior-dist)
                   post-30-dist (post params-30)
                   post-30-sampler (sampler post-30-dist {:limits (fge 2 4 [1 20 -400 100 0 20 0.01 100])})
                   post-300-dist (post params-300)
                   post-300-sampler (sampler post-300-dist {:limits (fge 2 4 [1 10 -400 100 0 20 0.01 100])})]
      (println (time (mix! post-30-sampler {:step 128})))
      (println (time (mix! post-300-sampler {:step 384})))
      (println (info post-300-sampler))
      [(histogram! post-30-sampler 1000)
       (histogram! post-300-sampler 1000)])))

(defn setup []
  (reset! state
          {:data @all-data
           :nu-30 (plot2d (qa/current-applet) {:width 350 :height 350})
           :b0-30 (plot2d (qa/current-applet) {:width 350 :height 350})
           :b1-30 (plot2d (qa/current-applet) {:width 350 :height 350})
           :sigma-30 (plot2d (qa/current-applet) {:width 350 :height 350})
           :nu-300 (plot2d (qa/current-applet) {:width 350 :height 350})
           :b0-300 (plot2d (qa/current-applet) {:width 350 :height 350})
           :b1-300 (plot2d (qa/current-applet) {:width 350 :height 350})
           :sigma-300 (plot2d (qa/current-applet) {:width 350 :height 350})}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (q/background 0)
    (q/image (show (render-histogram (:nu-30 @state) (@all-data 0) 0)) 0 0)
    (q/image (show (render-histogram (:b0-30 @state) (@all-data 0) 1)) 0 370)
    (q/image (show (render-histogram (:b1-30 @state) (@all-data 0) 2)) 0 740)
    (q/image (show (render-histogram (:sigma-30 @state) (@all-data 0) 3)) 0 1110)
    (q/image (show (render-histogram (:nu-300 @state) (@all-data 1) 0)) 370 0)
    (q/image (show (render-histogram (:b0-300 @state) (@all-data 1) 1)) 370 370)
    (q/image (show (render-histogram (:b1-300 @state) (@all-data 1) 2)) 370 740)
    (q/image (show (render-histogram (:sigma-300 @state) (@all-data 1) 3)) 370 1110)))

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

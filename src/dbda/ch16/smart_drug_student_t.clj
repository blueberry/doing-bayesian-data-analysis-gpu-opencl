;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch16.smart-drug-student-t
  (:require [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float]]
            [uncomplicate.fluokitten.core :refer [op]]
            [uncomplicate.neanderthal
             [core :refer [dim]]
             [real :refer [entry entry!]]
             [math :refer [sqrt]]
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

(let [in-file (slurp (io/resource "dbda/ch16/smart-drug.csv"))]
  (let [data (loop [s 0 p 0 data (drop 1 (csv/read-csv in-file))
                    smart (transient []) placebo (transient [])]
               (if data
                 (let [[b c] (first data)]
                   (case c
                     "Smart Drug"
                     (recur (inc s) p (next data) (conj! smart (double (read-string b))) placebo)
                     "Placebo"
                     (recur s (inc p) (next data) smart (conj! placebo (double (read-string b))))))
                 [(op [s] (persistent! smart))
                  (op [p] (persistent! placebo))]))]
    (def params {:smart-drug (fv (data 0))
                 :placebo (fv (data 1))})))

(defn analysis []
  (with-default-bayadera
    (with-release [smart-drug-prior
                   (library/distribution-model [:gaussian :uniform :exponential
                                                (slurp (io/resource "dbda/ch16/smart-drug-student-t.cl"))]
                                               {:name "smart_drug" :mcmc-logpdf "smart_drug_mcmc_logpdf"
                                                :params-size 5 :dimension 3})
                   prior (distribution smart-drug-prior)
                   prior-dist (prior (fv 10 100 60 0 100))
                   smart-drug-post (distribution "smart_drug" (library/likelihood-model :student-t) prior-dist)
                   smart-drug-dist (smart-drug-post (:smart-drug params))
                   smart-drug-sampler (sampler smart-drug-dist {:limits (fge 2 3 [0 30 80 120 0 40])})
                   placebo-post (distribution "placebo" (library/likelihood-model :student-t) prior-dist)
                   placebo-dist (placebo-post (:placebo params))
                   placebo-sampler (sampler placebo-dist {:limits (fge 2 3 [0 30 80 120 0 40])})]
      (println (time (mix! smart-drug-sampler {:step 256})))
      (println (time (mix! placebo-sampler {:step 256})))
      {:smart-drug (histogram! smart-drug-sampler 10)
       :placebo (histogram! placebo-sampler 10)})))

(defn setup []
  (reset! state
          {:data @all-data
           :smart-drug-nu (plot2d (qa/current-applet) {:width 500 :height 500})
           :smart-drug-mean (plot2d (qa/current-applet) {:width 500 :height 500})
           :smart-drug-std (plot2d (qa/current-applet) {:width 500 :height 500})
           :placebo-mean (plot2d (qa/current-applet) {:width 500 :height 500})
           :placebo-std (plot2d (qa/current-applet) {:width 500 :height 500})}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (q/background 0)
    (q/image (show (render-histogram (:smart-drug-nu @state) (:smart-drug @all-data) 0)) 0 0)
    (q/image (show (render-histogram (:smart-drug-mean @state) (:smart-drug @all-data) 1)) 0 520)
    (q/image (show (render-histogram (:smart-drug-std @state) (:smart-drug @all-data) 2)) 0 1040)
    (q/image (show (render-histogram (:placebo-mean @state) (:placebo @all-data) 1)) 520 520)
    (q/image (show (render-histogram (:placebo-std @state) (:placebo @all-data) 2)) 520 1040)))

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

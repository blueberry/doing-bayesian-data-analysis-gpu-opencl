;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch18.multiple-linear-regression
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
  (loop [data (drop 1 (csv/read-csv in-file)) res (transient [])]
    (if data
      (let [[_ spend _ _ prcnt-take _ _ satt] (first data)]
        (recur (next data)
               (-> res
                   (conj! (double (read-string spend)))
                   (conj! (double (read-string prcnt-take)))
                   (conj! (double (read-string satt))))))
      (fv (persistent! res)))))

(def data (fv (read-data (slurp (io/resource "dbda/ch18/sat-spending.csv")))))

(defn analysis []
  (with-default-bayadera
    (with-release [mlr-prior
                   (library/distribution-model [:gaussian :uniform :exponential :student-t
                                                (slurp (io/resource "dbda/ch18/multiple-linear-regression.cl"))]
                                               {:name "mlr" :mcmc-logpdf "mlr_mcmc_logpdf"
                                                :params-size 9 :dimension 5})
                   rhlr-likelihood
                   (library/likelihood-model (slurp (io/resource "dbda/ch18/multiple-linear-regression.cl"))
                                             {:name "mlr"})
                   prior (distribution mlr-prior)
                   prior-dist (prior (fv [26 0.001 1000 1000 500 0 20 0 5]))
                   post (distribution "mlr" rhlr-likelihood prior-dist)
                   post-dist (post data)
                   post-sampler (sampler post-dist {:limits (fge 2 5 [1 30 0.001 1000 0 2000 -20 20 -5 5])})]
      (println (time (mix! post-sampler {:cooling-schedule (pow-n 2)})))
      (println (time (do (burn-in! post-sampler 1000) (acc-rate! post-sampler))))
      (println (time (run-sampler! post-sampler 64)))
      (time (histogram! post-sampler 1000)))))

(defn setup []
  (reset! state
          {:data @all-data
           :nu (plot2d (qa/current-applet) {:width 500 :height 500})
           :sigma (plot2d (qa/current-applet) {:width 500 :height 500})
           :b0 (plot2d (qa/current-applet) {:width 500 :height 500})
           :b1 (plot2d (qa/current-applet) {:width 500 :height 500})
           :b2 (plot2d (qa/current-applet) {:width 500 :height 500})}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (let [data @all-data]
      (q/background 0)
      (q/image (show (render-histogram (:nu @state) data 0)) 0 0)
      (q/image (show (render-histogram (:sigma @state) data 1)) 520 0)
      (q/image (show (render-histogram (:b0 @state) data 2)) 0 520)
      (q/image (show (render-histogram (:b1 @state) data 3)) 0 1040)
      (q/image (show (render-histogram (:b1 @state) data 4)) 520 1040))))

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

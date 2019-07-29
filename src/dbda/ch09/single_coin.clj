;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch09.single-coin
  (:require [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float]]
            [uncomplicate.neanderthal
             [core :refer [row native dot imax imin scal! col submatrix vctr]]
             [real :refer [entry entry!]]
             [native :refer [fv fge]]]
            [uncomplicate.bayadera
             [core :refer :all]
             [library :as library]
             [distributions :refer [binomial-lik-params]]
             [util :refer [bin-mapper hdi]]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix! anneal! burn-in! acc-rate! run-sampler!]]]
            [uncomplicate.bayadera.internal.protocols :as p]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]))

(def all-data (atom {}))
(def state (atom nil))

(defn analysis []
  (with-default-bayadera
    (let [walker-count (* 256 44 32)
          sample-count (* 16 walker-count)
          z 9 N 12]
      (with-release [single-coin-model
                     (library/distribution-model [:beta (slurp (io/resource "dbda/ch09/single-coin.cl"))]
                                                 {:name "single_coin" :params-size 3 :dimension 2
                                                  :limits (fge 2 2 [0 1 0 1])})
                     prior (distribution single-coin-model)
                     prior-dist (prior (fv 2 2 100))
                     prior-sampler (time (doto (sampler prior-dist) (mix! {:a 2.68})))
                     prior-sample (dataset (sample prior-sampler sample-count))
                     prior-pdf (density prior-dist prior-sample)
                     binomial-lik (library/likelihood :binomial)
                     post (distribution "posterior" binomial-lik prior-dist)
                     coin-data (vctr prior-sample (binomial-lik-params N z))
                     post-dist (post coin-data)
                     post-sampler (time (doto (sampler post-dist) (mix!)))
                     post-sample (dataset (sample post-sampler sample-count))
                     post-pdf (scal! (/ 1.0 (evidence binomial-lik coin-data prior-sample))
                                     (density post-dist post-sample))]

        {:prior {:sample (native (submatrix (p/data prior-sample) 0 0 2 walker-count))
                 :pdf (native prior-pdf)
                 :histogram (histogram! prior-sampler 100)}
         :posterior {:sample (native (submatrix (p/data post-sample) 0 0 2 walker-count))
                     :pdf (native post-pdf)
                     :histogram (time (histogram! post-sampler 100))}}))))

(defn setup []
  (reset! state
          {:data @all-data
           :plots (repeatedly 6 (partial plot2d (qa/current-applet) {:width 400 :height 400}))}))

(defn draw-plots [[scatterplot omega theta] data ^long x-position ^long y-position]
  (q/image (show (render-sample scatterplot
                                (row (:sample data) 0)
                                (row (:sample data) 1)
                                (:pdf data)))
           x-position y-position)
  (q/image (show (render-histogram omega (:histogram data) 1 :rotate))
           (+ x-position 20 (width scatterplot)) y-position)
  (q/image (show (render-histogram theta (:histogram data) 0))
           x-position (+ y-position 20 (height scatterplot))))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (q/background 0)
    (draw-plots (:plots @state) (:prior @all-data) 0 0)
    (draw-plots (drop 3 (:plots @state)) (:posterior @all-data) 0 840)))

(defn display-sketch []
  (q/defsketch diagrams
    :renderer :p2d
    :size :fullscreen
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

;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    dbda.ch08.complete-example
  (:require [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release]]
            [uncomplicate.fluokitten.core :refer [op]]
            [uncomplicate.neanderthal
             [core :refer [row native scal! vctr]]
             [native :refer [fv]]]
            [uncomplicate.bayadera
             [core :refer [sampler dataset density distribution evidence sample]]
             [library :refer [beta likelihood]]
             [distributions :refer [beta-params binomial-lik-params]]
             [mcmc :refer [mix!]]
             [opencl :refer [with-default-bayadera]]]
            [uncomplicate.bayadera.internal.protocols :as p]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [scaling :refer [axis vector-axis]]
             [plots :refer [render-sample]]]))

(def all-data (atom {}))
(def plots (atom nil))

(defn analysis []
  (with-default-bayadera
    (let [a 1 b 1
          z 15 N 50]
      (with-release [prior-dist (beta a b)
                     prior-sampler (sampler prior-dist)
                     prior-sample (dataset (sample prior-sampler))
                     prior-pdf (density prior-dist prior-sample)
                     binomial-lik (likelihood :binomial)
                     coin-data (vctr prior-sample (binomial-lik-params N z))
                     post (distribution "beta_binomial" binomial-lik prior-dist)
                     post-dist (post coin-data)
                     post-sampler (time (doto (sampler post-dist) (mix!)))
                     post-sample (dataset (sample post-sampler))
                     post-pdf (scal! (/ 1.0 (evidence binomial-lik coin-data prior-sample))
                                     (density post-dist post-sample))]

        {:prior {:sample (native (row (p/data prior-sample) 0))
                 :pdf (native prior-pdf)}
         :posterior {:sample (native (row (p/data post-sample) 0))
                     :pdf (native post-pdf)}}))))

(defn setup []
  (reset! plots
          {:data @all-data
           :prior (plot2d (qa/current-applet) {:width 1000 :height 700})
           :posterior (plot2d (qa/current-applet) {:width 1000 :height 700})}))

(defn draw []
  (when-not (= @all-data (:data @plots))
    (swap! plots assoc :data @all-data)
    (q/background 0)
    (q/image (show (render (:prior @plots)
                           {:x-axis (axis 0 1) :x (:sample (:prior @all-data))
                            :y-axis (axis 0 2) :y (:pdf (:prior @all-data))})) 0 0)
    (q/image (show (render-sample (:posterior @plots)
                                  (:sample (:posterior @all-data))
                                  (:pdf (:posterior @all-data)))) 0 720)))

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

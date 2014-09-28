(ns reformer.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [json-html.core :refer  [edn->html]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros  [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [om-bootstrap.input :as i]
            [cljs.core.async :as async :refer [<! chan put! sliding-buffer close!]]
            [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [sablono.core :as html :refer-macros  [html]]
            [formative.core :as f]
            [formative.parse :as fp]
            [formative.dom :as fd]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [ajax.core :refer [POST]]))

(enable-console-print!)

(def mouse-down-ch
  (chan (sliding-buffer 1)))

(def mouse-up-ch
  (chan (sliding-buffer 1)))

(def mouse-move-ch
  (chan (sliding-buffer 1)))

(js/window.addEventListener  "mousedown" #(put! mouse-down-ch %))
(js/window.addEventListener  "mouseup" #(put! mouse-up-ch %))
(js/window.addEventListener  "mousemove" #(put! mouse-move-ch %))

(defn by-id [id]
  (.getElementById js/document id))

(defn set-position! [owner cursor]
  (let [node (om/get-node owner)] 
    (om/update! cursor :position {:top (.-offsetTop node)
                                  :left (.-offsetLeft node)})))

; (defn handle-drag-event [cursor owner evt-type e]
;   (when (= evt-type :down)
;     (println "evt down")
;       (om/set-state! owner :mouse {:pressed true}))
;   (when (:pressed (om/get-state owner :mouse))
;     (println "is pressed and " evt-type)
;     (when (= evt-type :up)
;     (println "up " evt-type)
;       (om/set-state! owner [:mouse :pressed] false))
;     (when (= evt-type :move)
;       (let [{:keys [offset-top offset-left]} (om/get-state owner :mouse)
;             node (om/get-node owner)]
;         ;(println (.-clientY e))
;         ;(println "target is " (get-id (.-target e)))
;         (om/update! cursor :position {:top (- (.-clientY e) 
;                                               (.-offsetTop node))
;                                       :left (- (.-clientX e) 
;                                                (.-offsetLeft node))})))))

(defn draggable [cursor owner {:keys [build-fn id comms] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:mouse-chan (chan (sliding-buffer 1000))
       :mouse {:offset-top 0 :offset-left 0 :pressed false}})
    om/IDidUpdate
    (did-update [_ _ _]
      (set-position! owner cursor))
    om/IDidMount
    (did-mount [_]
      ;(.addEventListener (om/get-node owner) "dragstart" (fn [e] (println "drag start")))
      ;(.addEventListener (om/get-node owner) "drag" (fn [e] (println "drag")))
      (set-position! owner cursor))
    ; om/IWillMount
    ; (will-mount [_]
    ;   (let [_ (println "Tapping " opts)
    ;         ;mouse-move (async/tap (get-in opts [:comms :mouse-move]) (chan))
    ;         mouse-move (async/tap (get-in opts [:comms :mouse-move]) (chan))
    ;         mouse-up (async/tap (get-in opts [:comms :mouse-up]) (chan))]

    ;     (go (while true 
    ;           (alt!
    ;             mouse-move ([e] (handle-drag-event cursor owner :move e))
    ;             mouse-up ([e] (handle-drag-event cursor owner :up e)))))))
    ; (will-mount [_]
    ;   (let [mouse-chan (om/get-state owner :mouse-chan)]
    ;     (go (while true
    ;           (let [[evt-type e] (<! mouse-chan)]
    ;             (handle-drag-event cursor owner evt-type e))))))
    om/IRenderState
    (render-state [_ {:keys [mouse-chan]}]
      (let [cursor-str (str (om/value cursor))
            value (om/value cursor)]
        (html 
          [:div {;:style {:width 1000}
                 :id id
                 :key id
                 :draggable true
                 ;:on-mouse-over (fn [e] (println "mouse over"))
                 :on-drag (fn [e] 
                            (println "Dragging " id))
                 :on-drag-over (fn [e] 
                                 (println "drag over " id)
                                 (.preventDefault e))
                 :on-drop (fn [e] 
                            (let [dropped (read-string (.getData (.-dataTransfer e) "text/plain"))]
                              (println "Dropped data " dropped)
                              (put! (:form-chan comms) {:from dropped :to value})
                              (println "Dropped onto " id)))
                 :on-drag-start (fn [e] 
                                  (.setData (.-dataTransfer e) "text/plain" cursor-str)
                                  (println "Drag start " id))
                 :on-drag-end (fn [e] (println "Drag end " id))
                 ;:on-mouse-enter (fn [e] (println "Mouse enter " id))
                 ; :on-mouse-up (fn [e] (println "Mouse up " id))
                 ; :on-mouse-down (fn [e] (handle-drag-event cursor owner :down e))
                 }
           build-fn]
          
          ))))) 

(def state (atom {:components {:c1 {:id :c1 :name "new"}
                               :c2 {:id :c2 :name "ones"}}
                  :form {:t1 {:id :t1 :name "hello" :ord 0}
                         :t2 {:id :t2 :name "shuttup" :ord 1}}}))

(defn update-ords [cursor from to]
  (om/transact! cursor
                (fn [m]
                  (let [from (if (:ord from)
                               from
                               (assoc from :ord (inc (apply max (map :ord (vals m))))))
                        from-ord (:ord from)
                        to-ord (:ord to)
                        update-ord (if (<= to-ord from-ord)
                                     (fn [ord]
                                       (cond (and (>= ord to-ord)
                                                  (< ord from-ord)) (inc ord)
                                             (= ord from-ord) to-ord
                                             :else ord))             
                                     (fn [ord]
                                       (cond (and (<= ord to-ord)
                                                  (> ord from-ord)) (dec ord)
                                             (= ord from-ord) to-ord
                                             :else ord)))
                        m-with-from (if-not (contains? m (:id from))
                                      (assoc m (:id from) from)
                                      m)]
                    (into {} 
                          (map 
                               (fn [[k v]] 
                                 [k (update-in v [:ord] update-ord)]))
                          m-with-from)))))

(defcomponent form-holder [app owner opts]
  (init-state [_]
              {:comms {:form-chan (chan)
                       :child-chan (chan (sliding-buffer 1))}})
  (will-mount [_]
              (let [_ (println "Tapping " opts)
                    form-chan (:form-chan (om/get-state owner :comms))]
                (go (while true 
                      (alt!
                        form-chan ([msg] (println "Form chan action" msg)
                                   (update-ords app 
                                                (:from msg)
                                                (:to msg))))))))
  (render-state [_ {:keys [comms]}]
          (html 
            [:div
             (map 
               (fn [[k v]]
                 (om/build draggable
                           v
                           {:opts {:comms (merge (om/get-shared owner :comms)
                                                 comms)
                                   :react-key (:name v)
                                   :id k
                                   :build-fn (d/form (i/input {:type "text" :addon-before "@" :value (:name v)})
                                                     (i/input {:type "text" :addon-after ".00"})
                                                     (i/input {:type "text" :addon-before "$" :addon-after ".00"})) }}))
               (sort-by (comp :ord val) 
                        app))])))

(defcomponent panels [{:keys [components form]} owner opts]
  (render-state [_ {:keys [comms]}]
                (html 
                  [:table
                   [:tbody
                    [:tr 
                     [:td 
                      (om/build form-holder components opts)
                      
                      ]
                     [:td 
                      (om/build form-holder form opts)

                      ]
                     ]
                    
                    ]
                   ]
                  )))


(om/root panels 
         state
         {:target (js/document.getElementById  "app")
          :shared {:comms {:mouse-down (async/mult mouse-down-ch)
                           :mouse-up (async/mult mouse-up-ch)
                           :mouse-move (async/mult mouse-move-ch)}}})

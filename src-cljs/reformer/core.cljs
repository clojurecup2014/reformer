(ns reformer.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [json-html.core :refer  [edn->html]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros  [defcomponent defcomponentk]]
            [om-tools.dom :as d :include-macros true]
            [om-bootstrap.input :as i]
            [om-bootstrap.grid :as g]
            [cljs.core.async :as async :refer [<! chan put! sliding-buffer close!]]
            [goog.events :as events]
            [datascript :as ds :refer [squuid]]
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

(defn draggable [cursor owner {:keys [build-fn id comms] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:mouse-chan (chan (sliding-buffer 1000))
       :mouse {:offset-top 0 :offset-left 0 :pressed false}})
    om/IRenderState
    (render-state [_ {:keys [mouse-chan]}]
      (let [cursor-str (str (om/value cursor))
            value (om/value cursor)]
        (html 
          [:div {:id id
                 :key id
                 :draggable true
                 :on-drag (fn [e] 
                            (println "Dragging " id))
                 :on-drag-over (fn [e] 
                                 (println "drag over " id)
                                 (.preventDefault e))
                 :on-drop (fn [e] 
                            (let [dropped (read-string (.getData (.-dataTransfer e) "text/plain"))]
                              (println "Dropped data " dropped)
                              (put! (:form-chan comms) [:drag-drop {:from dropped :to value}])
                              (println "Dropped onto " id)))
                 :on-drag-start (fn [e] 
                                  (.setData (.-dataTransfer e) "text/plain" cursor-str)
                                  (println "Drag start " id))
                 :on-drag-end (fn [e] (println "Drag end " id))}
           build-fn]))))) 

(def state (atom {:values {"currency" ""}
                  :components {:currency {:type :currency 
                                          :name "Currency"
                                          :addon-after ".00"
                                          :regex #"([0-9]+(\.[0-9]?[0-9]?)?)?"}
                               :first-name {:type :first-name 
                                            :name "First Name"
                                            :label "First Name" 
                                            :addon-after ""
                                            :regex #""}
                               :c2 {:type :currency :regex #"([0-9]+(\.[0-9]?[0-9]?)?)?"}}
                  :form {#uuid "53c5f509-775a-4b25-a425-26b5e67f2646" 
                         {:id #uuid "53c5f509-775a-4b25-a425-26b5e67f2646" 
                          :value-key "currency"
                          :label "Currency"
                          :type :currency 
                          :addon-after ".00"
                          :regex #"([0-9]+(\.[0-9]?[0-9]?)?)?"}}}))

(defn add-ord [component components]
  (if (:ord component)
    component
    (assoc component :ord (inc (apply max (map :ord components))))))

(defn add-id [component]
  (if (:id component)
    component
    (assoc component :id (squuid))))

(defn update-ords [cursor from to]
  (om/transact! cursor
                [:form]
                (fn [m]
                  (let [from (-> from
                                 (add-ord (vals m))
                                 add-id) 
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


(defcomponent bs-component [{:keys [component-info value] :as app} owner {:keys [comms] :as opts}]
  (render-state [_ {:keys [cursor-pos] :as state}]
                (println "value inside comp is " value " app " app)
                (let [form-chan (:form-chan comms)
                      value-key (:value-key component-info)
                      regex (:regex component-info)] 
                  (case (:type component-info)
                    :currency (i/input {:type "text" 
                                        :ref "input"
                                        :on-click (fn [e]
                                                    (doto (om/get-node owner "input")
                                                      .focus
                                                      .select)
                                                    (om/set-state! owner :cursor-pos nil))
                                        :on-input (fn [e]
                                                    (let [cursor-pos (.. e -target -selectionStart)
                                                          new-value (.. e -target -value)]
                                                      (if (some? (re-matches regex new-value)) 
                                                        (do 
                                                          (put! form-chan [:value-update {:value-key value-key :value new-value}])
                                                          (om/set-state! owner :cursor-pos cursor-pos))
                                                        (om/set-state! owner :cursor-pos (dec cursor-pos)))))
                                        :value value 
                                        :label (:label component-info) 
                                        :addon-after (:addon-after component-info)}))))
  (did-update [_ _ _]
              (when-let [cursor-pos (om/get-state owner :cursor-pos)]
                (println "Cursor pos " cursor-pos)
                (.setSelectionRange (om/get-node owner "input") cursor-pos cursor-pos))))

(defn form-chan-controller [app message]
  (let [[action arg] message]
    (prn "Controller " action arg)
    (case action
      :drag-drop (update-ords app (:from arg) (:to arg))
      :value-update (om/update! app [:values (:value-key arg)] (:value arg)))))

(defcomponent form-holder [{:keys [form components values] :as app} owner opts]
  (init-state [_]
              {:comms {:form-chan (chan)
                       :child-chan (chan (sliding-buffer 1))}})
  (will-mount [_]
              (let [form-chan (:form-chan (om/get-state owner :comms))]
                (go (while true 
                      (alt!
                        form-chan ([msg] (println "Form chan action" msg)
                                   (form-chan-controller app msg)))))))
  (render-state [_ {:keys [comms]}]
                (let [comms (merge (om/get-shared owner :comms) comms)] 
                  (html 
                    [:div
                     (map 
                       (fn [[k v]]
                         (let [draggable-cursor {:component-info v :value (get values (:value-key v))}]
                           (om/build draggable
                                     draggable-cursor
                                     {:opts {:comms comms 
                                             :react-key (:id v)
                                             :id k
                                             :build-fn (om/build bs-component 
                                                                 draggable-cursor
                                                                 {:opts {:comms comms}})}})))
                       (sort-by (comp :ord val) form))]))))

(defcomponent component-option [component owner opts]
  (render-state [_ _]
                (html [:li (:name component)])))

(defcomponent components-selector [components owner opts]
  ; (init-state [_]
  ;             {:comms {:form-chan (chan)
  ;                      :child-chan (chan (sliding-buffer 1))}})
  ; (will-mount [_]
  ;             (let [_ (println "Tapping " opts)
  ;                   form-chan (:form-chan (om/get-state owner :comms))]
  ;               (go (while true 
  ;                     (alt!
  ;                       form-chan ([msg] (println "Form chan action" msg)
  ;                                  (form-chan-controller app msg)))))))
  (render-state [_ {:keys [comms]}]
                (let [comms (merge (om/get-shared owner :comms)
                                   comms)] 
                  (html 
                    [:div
                     [:ul
                      (map 
                        (fn [[k v]]
                          (om/build draggable
                                    v
                                    {:opts {:comms comms 
                                            :react-key (:name v)
                                            :id k
                                            :build-fn (om/build component-option 
                                                                v
                                                                {:opts comms})}}))
                        (sort-by (comp :ord val) components))]]))))


(defcomponent panels [{:keys [components form values] :as app} owner opts]
  (render-state [_ {:keys [comms]}]
                (g/grid {}
                        (g/row {:class "show-grid"}
                               (g/col {:xs 6 :md 4}
                                      (om/build components-selector components opts))
                               (g/col {:xs 6 :md 4}
                                      (om/build form-holder app opts))))))

(om/root panels 
         state
         {:target (js/document.getElementById  "app")
          :shared {:comms {:mouse-down (async/mult mouse-down-ch)
                           :mouse-up (async/mult mouse-up-ch)
                           :mouse-move (async/mult mouse-move-ch)}}})

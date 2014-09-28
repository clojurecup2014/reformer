(ns reformer.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [json-html.core :refer  [edn->html]]
            [om.core :as om :include-macros true]
            [om-tools.core :refer-macros  [defcomponent defcomponentk]]
            [om-bootstrap.random :as r]
            [om-bootstrap.button :as b]
            [om-bootstrap.panel :as p]
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

;;;;; USE AT YOUR RISK.
;;;;; HACKED TOGETHER QUICKLY - didn't have much time for the clojurecup
;;;;; https://clojurecup.com

(defn draggable [cursor owner {:keys [build-fn id comms accept-drop?] :as opts}]
  (reify
    om/IRenderState
    (render-state [_ {:keys []}]
      (let [component-info (om/value (:component-info cursor))
            component-info-str (str component-info)
            form-chan (:form-chan comms)]
        (html 
          [:div {:id id
                 :key id
                 :draggable true
                 :on-drag (fn [e] 
                            #_(println "Dragging " id))
                 :on-drag-over (fn [e] 
                             ;    (println "drag over " id)
                                 (.preventDefault e))
                 :on-drop (fn [e] 
                            (when accept-drop?
                              (let [dropped (read-string (.getData (.-dataTransfer e) "text/plain"))]
                                (put! form-chan [:drag-drop {:from dropped :to component-info}]))))
                 :on-drag-start (fn [e] 
                                  (.setData (.-dataTransfer e) "text/plain" component-info-str))}
           build-fn]))))) 

(def countries ["Afghanistan", "Albania","Algeria","Andorra","Angola","Antigua & Deps",
                "Argentina","Armenia","Australia","Austria","Azerbaijan","Bahamas","Bahrain",
                "Bangladesh","Barbados","Belarus","Belgium","Belize","Benin","Bhutan","Bolivia","Bosnia Herzegovina",
                "Botswana","Brazil","Brunei","Bulgaria","Burkina","Burundi","Cambodia","Cameroon","Canada","Cape Verde",
                "Central African Rep","Chad","Chile","China","Colombia","Comoros","Congo","Congo {Democratic Rep}",
                "Costa Rica","Croatia","Cuba","Cyprus","Czech Republic","Denmark","Djibouti","Dominica",
                "Dominican Republic","East Timor","Ecuador","Egypt","El Salvador","Equatorial Guinea","Eritrea","Estonia",
                "Ethiopia","Fiji","Finland","France","Gabon","Gambia","Georgia","Germany","Ghana","Greece","Grenada",
                "Guatemala","Guinea","Guinea-Bissau","Guyana","Haiti","Honduras","Hungary","Iceland","India","Indonesia",
                "Iran","Iraq","Ireland {Republic}","Israel","Italy","Ivory Coast","Jamaica","Japan","Jordan","Kazakhstan",
                "Kenya","Kiribati","Korea North","Korea South","Kosovo","Kuwait","Kyrgyzstan","Laos","Latvia","Lebanon",
                "Lesotho","Liberia","Libya","Liechtenstein","Lithuania","Luxembourg","Macedonia","Madagascar","Malawi",
                "Malaysia","Maldives","Mali","Malta","Marshall Islands","Mauritania","Mauritius","Mexico","Micronesia",
                "Moldova","Monaco","Mongolia","Montenegro","Morocco","Mozambique","Myanmar, {Burma}","Namibia","Nauru",
                "Nepal","Netherlands","New Zealand","Nicaragua","Niger","Nigeria","Norway","Oman","Pakistan","Palau",
                "Panama","Papua New Guinea","Paraguay","Peru","Philippines","Poland","Portugal","Qatar","Romania",
                "Russian Federation","Rwanda","St Kitts & Nevis","St Lucia","Saint Vincent & the Grenadines",
                "Samoa","San Marino","Sao Tome & Principe","Saudi Arabia","Senegal","Serbia","Seychelles",
                "Sierra Leone","Singapore","Slovakia","Slovenia","Solomon Islands","Somalia","South Africa",
                "South Sudan","Spain","Sri Lanka","Sudan","Suriname","Swaziland","Sweden","Switzerland","Syria",
                "Taiwan","Tajikistan","Tanzania","Thailand","Togo","Tonga","Trinidad & Tobago","Tunisia","Turkey",
                "Turkmenistan","Tuvalu","Uganda","Ukraine","United Arab Emirates","United Kingdom",
                "United States","Uruguay","Uzbekistan","Vanuatu","Vatican City","Venezuela","Vietnam",
                "Yemen","Zambia","Zimbabwe"])

(def state (atom {:values {#uuid  "001ec685-b3f7-4780-982d-8487f554445d" ""
                           #uuid "542892c0-40cc-4f2d-b15b-64dea375f4bb" ""}
                  :components {:first-name {:type "text"
                                            :bs-type :input
                                            :name "First Name"
                                            :label "First Name" 
                                            :validate-regex #".+"
                                            :regex #".*"}
                               :last-name {:type "text"
                                           :bs-type :input
                                           :validate-regex #".+"
                                           :name "Last Name"
                                           :label "Last Name" 
                                           :regex #".*"}
                               :number {:type "text"
                                        :bs-type :input
                                        :name "Number"
                                        :label "Number" 
                                        :regex #"[0-9]*(\.[0-9]*)?"}
                               :country {:type "dropdown"
                                         :bs-type :dropdown
                                         :items countries
                                         :name "Country"
                                         :title "Country"}
                               :price {:type "text"
                                       :bs-type :input
                                       :name "Price"
                                       :label "Price"
                                       :addon-before "$"
                                       :addon-after ".00"
                                       :regex #"([0-9]+(\.[0-9]?[0-9]?)?)?"}
                               :text-area {:bs-type :input
                                           :type "textarea"
                                           :help "Some help text"
                                           :name "Text Area"
                                           :label "Text Area"
                                           :regex #".*"}
                               :phone-number {:type "text"
                                              :bs-type :input
                                              :name "Phone Number"
                                              :label "Phone Number"
                                              :validate-regex #"\+?[0-9\ \(\)]+"
                                              :regex #"\+?[0-9\ \(\)]*"}
                               :email-address {:type "text"
                                               :bs-type :input
                                               :feedback? true
                                               :addon-before "@"
                                               :name "Email Address"
                                               :label "Email Address" 
                                               :validate-regex #"\S+@\S+\.\S+"
                                               :regex #".*"}}
                  :form {#uuid "53c5f509-775a-4b25-a425-26b5e67f2646" 
                         {:id #uuid "53c5f509-775a-4b25-a425-26b5e67f2646" 
                          :value-key #uuid  "001ec685-b3f7-4780-982d-8487f554445d"
                          :ord 0
                          :type "text"
                          :bs-type :input
                          :name "First Name"
                          :label "First Name" 
                          :validate-regex #".+"
                          :regex #".*"}
                         #uuid "542892ad-9305-4623-8967-cb8ca92b34ea"
                         {:id #uuid "542892ad-9305-4623-8967-cb8ca92b34ea"
                          :value-key #uuid "542892c0-40cc-4f2d-b15b-64dea375f4bb"
                          :ord 1 
                          :type "text"
                          :bs-type :input
                          :name "Last Name"
                          :label "Last Name" 
                          :validate-regex #".+"
                          :regex #".*"}}}))

(defn add-ord [component components]
  (if (:ord component)
    component
    (assoc component :ord (inc (apply max (map :ord components))))))

(defn add-id [component]
  (if (:id component)
    component
    (assoc component :id (squuid))))

(defn add-value-key [component]
  (if (:value-key component)
    component
    (assoc component :value-key (squuid))))

(defcomponent bs-component [{:keys [component-info value] :as app} owner {:keys [comms] :as opts}]
  (render-state [_ {:keys [cursor-pos] :as state}]
                (let [form-chan (:form-chan comms)
                      value-key (:value-key component-info)
                      id (:id component-info)
                      regex (:regex component-info)
                      validate-regex (:validate-regex component-info)] 
                  (case (:bs-type component-info)
                    :dropdown (apply (partial b/dropdown 
                                              (merge {:bs-style "default" 
                                                      :style {:width 200}
                                                      :title (if (empty? value) 
                                                               (:title component-info)
                                                               value)}
                                                     (select-keys component-info [])))
                                     (map (fn [v]
                                            (b/menu-item 
                                              {:on-select (fn [e] (put! form-chan 
                                                                        [:value-update {:value-key value-key :value v}]))
                                               :key v} v)) 
                                          (:items component-info)))
                    :input (i/input (merge {:ref "input"
                                            :bs-style (cond (nil? validate-regex) nil
                                                            (some? (re-matches validate-regex value)) "success"
                                                            :else "error")
                                            :on-click (fn [e]
                                                        (put! form-chan [:edit-component {:id id}])
                                                        (om/set-state! owner :cursor-pos nil)
                                                        (doto (om/get-node owner "input") .focus .select))
                                            :on-blur (fn [e] (om/set-state! owner :cursor-pos nil))
                                            :on-input (fn [e]
                                                        (let [cursor-pos (.. e -target -selectionStart)
                                                              new-value (.. e -target -value)]
                                                          (if (some? (re-matches regex new-value)) 
                                                            (do 
                                                              (put! form-chan [:value-update {:value-key value-key 
                                                                                              :value new-value}])
                                                              (om/set-state! owner :cursor-pos cursor-pos))
                                                            (om/set-state! owner :cursor-pos (dec cursor-pos)))))
                                            :value value 
                                            :label (:label component-info)}
                                           (select-keys component-info [:addon-before 
                                                                        :addon-after
                                                                        :feedback? 
                                                                        :label-classname 
                                                                        :wrapper-classname 
                                                                        :type]))))))
  (did-update [_ _ _]
              (when-let [cursor-pos (om/get-state owner :cursor-pos)]
                (.setSelectionRange (om/get-node owner "input") cursor-pos cursor-pos))))

(defn update-form [cursor from to]
  (om/transact! cursor
                []
                (fn [c]
                  (let [m (:form c)
                        from (-> from
                                 (add-ord (vals m))
                                 add-value-key
                                 add-id) 
                        value-key (:value-key from)
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
                    (-> c
                        (update-in [:values value-key] #(or % ""))
                        (assoc-in [:form]
                                  (into {} 
                                        (map 
                                          (fn [[k v]] 
                                            [k (update-in v [:ord] update-ord)]))
                                        m-with-from)))))))

(defn form-chan-controller [app message]
  (let [[action arg] message]
    (case action
      :edit-component (om/update! app [:editing] (:id arg)) 
      :update-component (om/update! app [:form (:id arg) (:key arg)] (:value arg)) 
      :drag-drop (update-form app (:from arg) (:to arg))
      :value-update (om/update! app [:values (:value-key arg)] (:value arg)))))

(defcomponent form-holder [{:keys [form components values] :as app} owner opts]
  (render-state [_ _]
                (html 
                  [:div 
                   (d/form {}
                           (map 
                             (fn [[k v]]
                               (let [draggable-cursor {:component-info v :value (get values (:value-key v))}]
                                 (om/build draggable
                                           draggable-cursor
                                           {:react-key (:id v)
                                            :opts {:comms (:comms opts) 
                                                   :id k
                                                   :accept-drop? true
                                                   :build-fn (om/build bs-component 
                                                                       draggable-cursor
                                                                       {:opts opts})}})))
                             (sort-by (comp :ord val) form)))])))

(defcomponent component-option [component owner opts]
  (render-state [_ _]
                (html [:li {:class "list-group-item"} (:name component) ])))

(defcomponent components-selector [components owner opts]
  (render-state [_ _]
                (p/panel
                  {:header "Form Components Panel"
                   :bs-style "success"
                   :list-group (html [:ul {:class "list-group"}
                                      (map 
                                        (fn [[k v]]
                                          (let [draggable-cursor {:component-info v}] 
                                            (om/build draggable
                                                      draggable-cursor 
                                                      {:react-key (:name v)
                                                       :opts {:comms (:comms opts) 
                                                              :accept-drop? false
                                                              :id k
                                                              :build-fn (om/build component-option 
                                                                                  v
                                                                                  {:opts opts})}})))
                                        (sort-by (comp :ord val) components))])} 
                  nil)))

(defcomponent edit-form-control [{:keys [label id] :as component} owner opts]
  (render-state [_ _]
                (let [form-chan (get-in opts [:comms :form-chan])] 
                  (html [:div "Form Control Editor"
                         (i/input {:ref "input"
                                   :type "text"
                                   :on-input (fn [e]
                                               (let [cursor-pos (.. e -target -selectionStart)
                                                     new-value (.. e -target -value)]
                                                 (put! form-chan [:update-component {:id id 
                                                                                     :key :label 
                                                                                     :value new-value}])))
                                   :value label 
                                   :label "Edit Label"})]))))


(defcomponent panels [{:keys [components editing form values] :as app} owner opts]
  (init-state [_]
              {:comms {:form-chan (chan)
                       :child-chan (chan (sliding-buffer 1))}})
  (will-mount [_]
              (let [form-chan (:form-chan (om/get-state owner :comms))]
                (go (while true 
                      (alt!
                        form-chan ([msg] #_(println "Form chan action" msg)
                                   (form-chan-controller app msg)))))))
  (render-state [_ {:keys [comms]}]
                (let [comms (merge (om/get-shared owner :comms) comms)] 
                  (html [:div
                         (r/page-header {} 
                                        "Welcome to reformer.")
                         (r/well {} (d/small
                                      "To use, drag from the components panel on the left onto an element 
                                      in the form on the right. You can also drag re-order form controls within the form.
                                      Form components can validate input in a blocking way (say a phone number), 
                                      or validate but allow input (e.g. email address).
                                      The idea was to supply a drop in js file that you could place on any page 
                                      and pass in the edn/json to have the entire form built for you without any coding required.
                                      Unfortunately, I may need something like transit to do the last part properly. In addition, 
                                      future improvements will include editing of validation regexes and 
                                      implementation of additional controls. "
                                      "Please " (html [:a {:href "https://clojurecup.com/#/apps/reformer"} " vote for me "])
                                      "at the clojure cup if you're interested in this idea being developed further. "
                                      (html [:a {:href "https://twitter.com/ghaz"} 
                                             "Follow me on twitter."])))
                         (r/well {}
                                 (if-let [selected-component (get form editing)] 
                                   (om/build edit-form-control selected-component {:opts {:comms comms}})))
                         (g/grid {}
                                 (g/row {:class "show-grid"}
                                        (g/col {:xs 6 :md 4}
                                               (om/build components-selector components {:opts {:comms comms}}))
                                        (g/col {:xs 6 :md 4}
                                               (om/build form-holder app {:opts {:comms comms}})))
                                 (g/row {:class "show-grid"}
                                        (html [:div (str (om/value form))])))]))))

(om/root panels 
         state
         {:target (js/document.getElementById  "app")
          :shared {:comms {}}})

(ns huge-feedback.config
  (:require
    [clojure.tools.reader.edn :as edn]
    [huge-feedback.apis.gitlab :as gitlab]
    [re-frame.core :as rf]
    [reagent.core :as r]
    [clojure.spec.alpha :as s]
    [huge-feedback.routes :as routes]))

(defn handle-test-response [[ok? resp] config]
  (if ok?
    (rf/dispatch [:set-config config])
    (rf/dispatch [:invalid-config config (str "Got error when attempting test request to gitlab: " \newline resp)])))

(s/def ::config (s/keys :req [::gitlab/config
                              :huge-feedback.core/use-cors-proxy?]))

(defn validate-then-set-config [config]
  (cond
    (nil? config) (rf/dispatch [:invalid-config "Default config not available"])
    (s/valid? ::gitlab/config (::gitlab/config config)) (do
                                                          (rf/dispatch [:validating-config config])
                                                          (rf/dispatch [:ajax-request (gitlab/test-request (::gitlab/config config) #(handle-test-response %1 config))]))
    :default (rf/dispatch [:invalid-config config (s/explain-str ::gitlab/config (::gitlab/config config))])))

(defn text-editor [initial-value on-save & [validate]]
  (let [state (r/atom {:input initial-value :valid? true})]
    (fn []
      [:form
       [:textarea {:class         (if (:valid? @state) "" "invalid")
                   :on-change     #(->> %1 (.-target) (.-value) (swap! state assoc :input))
                   :default-value initial-value}]
       [:input {:type     "submit"
                :on-click #(let [{:keys [input]} @state]
                             (.preventDefault %1)
                             (if (or (nil? validate) (validate input))
                               (on-save input)
                               (swap! state assoc :valid? false)))}]])))

(rf/reg-sub :config-state
  (fn [{:keys [::config-state ::config-message]}]
    [config-state config-message]))

(defn status [& [link?]]
  (fn []
    (let [[state message] @(rf/subscribe [:config-state])]
      [:div.config-status
       (case state
         ::requesting [:div [:p "Getting default config"]]
         ::invalid [:div.warning
                    [:p "Configuration is invalid: "]
                    [:pre message]]
         ::validating [:div [:p "Validating configuration..."]]
         ::valid [:div [:p "Config ok"]])
       (when link?
         (routes/link-for "Edit config" :config))])))

(defn panel []
  [:div
   [status]
   [text-editor (str @(rf/subscribe [:config]))
    #(rf/dispatch [:config (edn/read-string %1)])
    #(try (let [config (edn/read-string %1)]
            (validate-then-set-config config))
          (catch js/Error e
            (rf/dispatch [:invalid-config (.-message e)])
            false))]])


(rf/reg-event-db :validating-config
  (fn [db [_ config]] (-> db
                          (assoc ::config-state ::validating))))

(rf/reg-event-db :set-config
  (fn [{:keys [::config-state] :as db} [_ config]]
    (-> db
        (assoc ::config config)
        (assoc ::config-state ::valid)
        (dissoc ::config-message))))

(rf/reg-event-db :invalid-config
  (fn [db [_ message]]
    (-> db
        (assoc ::config-state ::invalid)
        (assoc ::config-message message))))
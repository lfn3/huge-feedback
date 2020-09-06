(ns huge-feedback.routes
  (:require [bidi.bidi :as bidi]
            [clojure.tools.reader.edn :as edn]
            #?@(:cljs [[re-frame.core :as rf]]
                :clj  [[huge-feedback.handlers :as handlers]])
            [clojure.walk :as walk]))

(def index-key ::index)

(def clientside-routes ["/" [["" index-key]
                             ["pipeline-detail" ::pipeline-detail]
                             ["job-detail" ::job-detail]
                             ["config" :config]]])

(defn clientside-routes-for-server [routes]
  "This is almost certainly incorrect, but probably sufficient for now."
  (->> routes
       (last)                                               ; Strip off the leading /
       (walk/postwalk (fn [x] (if (keyword? x) index-key x)) )))

(def serverside-routes ["/" (-> clientside-routes
                                (clientside-routes-for-server)
                                (concat
                                  [["proxy" :huge-feedback.handlers/proxy]
                                   ["config.edn" :huge-feedback.handlers/config.edn]
                                   [true :huge-feedback.handlers/resources]])
                                (vec))])

#?(:clj (def serverside-handler-map {index-key                         handlers/index
                                     :huge-feedback.handlers/proxy     handlers/proxy-request
                                     :huge-feedback.handlers/config.edn     handlers/config-edn
                                     :huge-feedback.handlers/resources handlers/resources}))

(defn path-for [handler params]
  (let [serverside (apply bidi/path-for serverside-routes handler params)
        clientside (apply bidi/path-for clientside-routes handler params)
        on-click #?(:clj false
                   :cljs (not (nil? clientside)))
        selected #?(:clj (or serverside clientside)
                   :cljs (or clientside serverside))]
    [on-click selected]))

(defn client-server-mismatch-message [url]
  (str "For some reason the server decided the client should handle " url ", but the client disagrees."))

(defn parse-url [url]
  (let [clientside-match (bidi/match-route clientside-routes url)
        serverside-match (bidi/match-route serverside-routes url)]
    #?(:clj (cond
              serverside-match serverside-match
              clientside-match index-key) ;Return the index page, and let the client side url parsing sort it out.
       :cljs (cond
                clientside-match clientside-match
                serverside-match (throw (js/Error. (client-server-mismatch-message url)))))))

#?(:cljs (do
            (rf/reg-event-fx :navigate-to-panel
              (fn [{:keys [db]} [_ url route-map]]
                {:db         (assoc db :active-panel route-map)
                 :push-state [url route-map]}))

            (rf/reg-event-db :set-active-panel
              (fn [db [_ route-map]]
                (assoc db :active-panel route-map)))

            (rf/reg-fx :push-state
                       (fn [[url state :as value]]
                         (.pushState (.-history js/window)
                                     (pr-str state)
                                     ""
                                     url)))
            (defn handle-pop-state [evt]
              (let [state (edn/read-string (.-state evt))]
                (rf/dispatch [:set-active-panel state])))

            (defn on-click-handler [url handler params-map]
              (fn [evt]
                (.preventDefault evt)
                (rf/dispatch [:navigate-to-panel url (merge {:handler handler} params-map)])))))

(defn attrs-for [handler params]
  (let [[on-click? url] (path-for handler params)]
    #?(:clj {:href url}
       :cljs (cond-> {:href url}
                     on-click? (assoc :on-click (on-click-handler url handler (apply hash-map params)))))))

(defn link-for [text handler & params]
  (let [attrs (attrs-for handler params)]
    [:a attrs text]))

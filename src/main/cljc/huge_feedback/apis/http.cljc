(ns huge-feedback.apis.http
  (:refer-clojure :exclude [get])
  (:require [ajax.core]
            [ajax.json]
            [ajax.edn]
            [ajax.ring]
            [clojure.spec.alpha :as s]
            #?@(:cljs [[re-frame.core :as rf]])))

(def local-format {:format          (ajax.edn/edn-request-format)
                   :response-format (ajax.ring/ring-response-format
                                      {:format (ajax.edn/edn-response-format)})})

(def json-format {:format          (ajax.json/json-request-format)
                  :response-format (ajax.ring/ring-response-format
                                     {:format (ajax.json/json-response-format {:keywords? true})})})

(def formats {::proxy local-format
              ::local local-format
              ::json  json-format})

(defn hydrate-req [req-map]
  (-> req-map
      (merge (clojure.core/get formats (::format req-map)))))

(defn should-proxy? [req-map] (::proxy? req-map))

(defn req-map->proxy-req-map [req-map]
  (let [{:keys [::format :handler]} req-map]
    (merge req-map {::format ::proxy
                    :handler (fn [])})))

(s/def ::format (keys formats))
(s/def ::proxy? boolean?)
(s/def ::handler fn?)
(s/def ::method #{"GET" "POST"})
(s/def ::uri string?)

(s/def ::req-map (s/keys :req [::format]
                         :opt [::proxy?]
                         :req-un [::handler ::method ::uri]))

(defn to-ajax-req-map [req-map]
  (if (should-proxy? req-map)
    (req-map->proxy-req-map req-map)
    (hydrate-req req-map)))

(defn execute [req-map]
  (ajax.core/ajax-request (to-ajax-req-map req-map)))

(defn with-paginator-handler [{:keys [handler] :as req-map} extract-next-url-fn]
  (let [paginator-handler (fn paginator-handler [[ok? resp]]
                            (handler [ok? resp])
                            (when-let [next-url (extract-next-url-fn resp)]
                              (execute (merge req-map {:handler paginator-handler
                                                       :uri next-url}))))]
    (assoc req-map :handler paginator-handler)))

(defn paginated-get
  "Handler will be called multiple times with each page of results.
   next-page-fn should return the url of the next page, or nil once complete.
   Note next-page-fn is fed the whole result, while handler only gets the body.
   next-page-fn should return nil to stop."
  [url next-page-fn handler & [headers]]
  (let [headers (or headers {})]
    (let [handler-with-next-page (fn [response]
                                   (when-let [next-url (next-page-fn response)]
                                     (paginated-get next-url next-page-fn handler headers))
                                   (handler (:body response)))]
      (ajax.core/GET url {:format          (ajax.json/json-request-format)
                          :response-format (ajax.ring/ring-response-format
                                             {:format (ajax.json/json-response-format {:keywords? true})})
                          :headers         headers
                          :handler         handler-with-next-page}))))

(defn get
  "The handler only gets the body of the response."
  [url handler & [headers]]
  (paginated-get url (constantly nil) (comp :body handler) headers))

(defmacro sync!
  "Assumes the last element in body is a call taking handler as it's final argument,
   and makes it synchronous using a promise. Won't work right on paginated-get"
  [& body]
  (let [psym (gensym)
        handler `(#(deliver ~psym %1))]
    `(let [~psym (promise)]
       ~@(drop-last body)
       ~(concat (last body) handler)
       (deref ~psym))))

#?(:cljs (do
           (rf/reg-event-fx :ajax-request
             (fn [cofx [_ req-map]]
               (assoc cofx :ajax-request req-map)))

           (rf/reg-fx :ajax-request
            (fn [req-map]
              (execute req-map)))))
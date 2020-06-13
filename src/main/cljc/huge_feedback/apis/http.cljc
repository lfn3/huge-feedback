(ns huge-feedback.apis.http
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

(defn req-map->proxy-req-map [{:keys [handler] :as req-map}]
  (-> {:body    (dissoc req-map :handler)
       :method  "POST"
       :uri     "/proxy"
       ::format ::proxy
       :handler (fn [[outer-ok? outer-resp]] (when outer-ok? (handler (:body outer-resp))))}
      (hydrate-req)))

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

(defn execute-sync [req-map]
  (let [handler (or (:handler req-map) identity)
        p (promise)
        sync-handler #(deliver p %1)
        sync-req-map (assoc req-map :handler sync-handler)]
    (execute sync-req-map)
    (handler @p)))

#?(:cljs (do
           (rf/reg-event-fx :ajax-request
             (fn [cofx [_ req-map]]
               (assoc cofx :ajax-request req-map)))

           (rf/reg-fx :ajax-request
            (fn [req-map]
              (execute req-map)))))
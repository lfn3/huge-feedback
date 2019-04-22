(ns huge-feedback.apis.http
  (:refer-clojure :exclude [get])
  (:require [ajax.core]))

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
      (ajax.core/GET url {:format          :json
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

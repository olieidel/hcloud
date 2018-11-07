(ns hcloud.core-test
  "Unfinished tests"
  (:require [clojure.test :refer :all]
            [hcloud.core :as core]
            [org.httpkit.server :as http-kit]))


(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn handler [req]
  {:status  200
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str (dissoc req :async-channel))})

(defn start-server []
  (reset! server (http-kit/run-server #'handler {:port 8080})))

(comment
  (start-server)
  (stop-server)
  (require '[clj-http.client :as http])
  (read-string (:body (http/get "http://127.0.0.1:8080"))))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

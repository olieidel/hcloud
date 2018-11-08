(ns readme
  "Utilities for populating the README with docs from the source."
  (:require [clojure.string :as str]
            [flatland.ordered.map :refer [ordered-map]]
            hcloud.core))


(def ^:private ^:const readme-path
  "Path to the `README.md`."
  "./README.md")

(def ^:private ^:const begin-string
  "The string to look for in the README for to begin inserting the documentation."
  "<!-- AUTO-GENERATED DOCUMENTATION BEGIN -->")

(def ^:private ^:const end-string
  "The string to look for in the README for to end inserting the documentation."
  "<!-- AUTO-GENERATED DOCUMENTATION END -->")

(def ^:private api-categories
  "Mapping of `:api-category` keywords (metadata of public functions in
  `hcloud.core`) to human-readable strings."
  (ordered-map
   :actions             "Actions"
   :servers             "Servers"
   :server-actions      "Server Actions"
   :floating-ips        "Floating IPs"
   :floating-ip-actions "Floating IP Actions"
   :ssh-keys            "SSH Keys"
   :server-types        "Server Types"
   :locations           "Locations"
   :datacenters         "Datacenters"
   :images              "Images"
   :image-actions       "Image Actions"
   :isos                "ISOs"
   :pricing             "Pricing"))

(defn- generate-docs []
  (letfn [(fn-data []
            (->> (map (comp meta val) (ns-interns 'hcloud.core))
                 (filter #(contains? % :api-category))
                 ;; sort by `:order` because `ns-interns` gives us a
                 ;; map of things defined in the namespace which
                 ;; jumbles up the order - but we want the fns to be
                 ;; ordered the same way as in the Hetzner docs
                 (sort-by :order)
                 (group-by :api-category)))
          (fn-docs [data]
            {:first-line "Foo"
             :rest "Bar"}
            (let [lines (str/split-lines (:doc data))]
              {:first-line (first lines)
               :rest (str/join "\n" (map str/trim (rest lines)))}))
          (fn-arglists [data]
            (str/join "\n\n"
                      (for [arglist (:arglists data)]
                        (str "`" (cons (:name data) arglist) "`"))))
          (fn-data->markdown [data]
            (let [docs (fn-docs data)]
              (str "#### `" (:name data) "`: " (:first-line docs) "\n\n"
                   (fn-arglists data)
                   "\n"
                   (:rest docs))))]
    (let [data (fn-data)]
      (str/join
       "\n\n"
       (for #_[[category fns-data-of-category] (fn-data)]
            [[category category-description] api-categories]
         (str "### " category-description "\n\n"
              (str/join "\n\n"
                        (map fn-data->markdown (get data category)))))))))


(defn- insert-docs!
  "Insert auto-generted docs (a string) into the readme between
  `begin-string` and `end-string`. Writes to `README.md`"
  [docs]
  (let [readme-lines (str/split-lines (slurp readme-path))
        before (take-while #(not= begin-string %) readme-lines)
        after (rest (drop-while #(not= end-string %) readme-lines))]
    (->> (concat before
                 [begin-string docs end-string]
                 after)
         (str/join "\n")
         (spit readme-path))))

(defn regenerate-docs!
  "Regenerate the docs from the source code and insert them into the
  README. This is probably the function you're looking for."
  []
  (insert-docs! (generate-docs)))


(comment
  (generate-docs)
  (regenerate-docs!))

(ns converter.traktor.location
  (:require
   [cemerick.url :refer [url url-encode url-decode]]
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as string]
   [clojure.zip :as zip]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.traktor.nml :as nml]
   [converter.url :as url]
   [spec-tools.data-spec :as std]))

(def location
  {:tag (s/spec #{:LOCATION})
   :attrs {:DIR ::nml/nml-dir
           :FILE ::str/not-blank-string
           (std/opt :VOLUME) (std/or {:drive-letter ::str/drive-letter
                                      :not-drive-letter ::str/not-blank-string})
           (std/opt :VOLUMEID) ::str/not-blank-string}})

(def location-spec
  (spec/such-that-spec
   (std/spec {:name ::location
              :spec location})
   #(or (and (-> % :attrs :VOLUME) (-> % :attrs :VOLUMEID))
        (and (not (-> % :attrs :VOLUME)) (not (-> % :attrs :VOLUMEID))))))

(s/fdef url->location
  :args (s/cat :url ::url/url)
  :ret location-spec
  :fn (fn equiv-location? [{{conformed-url :url} :args conformed-location :ret}]
        (let [url (s/unform ::url/url conformed-url)
              location-z (zip/xml-zip (s/unform location-spec conformed-location))]
          (re-matches nml/nml-dir-regex (zx/attr location-z :DIR)))))

(defn url->location
  [{:keys [:path]}]
  (let [paths (rest (string/split path #"/"))
        dirs (if (str/drive-letter? (first paths)) (rest (drop-last paths)) (drop-last paths))
        file (last paths)
        volume (if (str/drive-letter? (first paths)) (first paths))]
    {:tag :LOCATION
     :attrs (cond-> {:DIR (nml/nml-dir (map url-decode dirs))
                     :FILE (url-decode file)}
              volume (assoc :VOLUME volume))}))

(s/fdef location->url
  :args (s/cat :location-z (spec/xml-zip-spec location-spec))
  :ret ::url/url)

(defn location->url
  [location-z]
  (let [dir (zx/attr location-z :DIR)
        file (zx/attr location-z :FILE)
        volume (zx/attr location-z :VOLUME)]
    (apply url (as-> [] $
                 (conj $ "file://localhost")
                 (conj $ (if (str/drive-letter? volume) (str "/" volume) ""))
                 (reduce conj $ (map url-encode (string/split dir nml/nml-path-sep-regex)))
                 (conj $ (url-encode file))))))
(ns lens.handler.study
  (:use plumbing.core)
  (:require [clojure.core.reducers :as r]
            [clojure.string :as str]
            [liberator.core :refer [resource]]
            [lens.api :as api]
            [lens.util :as util]
            [lens.handler.util :refer :all]
            [lens.reducers :as lr]))

(defn all-studies-path
  ([path-for] (all-studies-path path-for 1))
  ([path-for page-num] (path-for :all-studies-handler :page-num page-num)))

(defn study-path [path-for study]
  (path-for :study-handler :study-id (:study/id study)))

(defn study-link [path-for study]
  {:href (study-path path-for study) :title (:study/name study)})

(def find-study-handler
  (resource
    (standard-redirect-resource-defaults)

    :processable?
    (fnk [[:request params]]
      (:id params))

    :location
    (fnk [[:request path-for [:params id]]]
      (study-path path-for {:study/id id}))))

(defn child-list-path
  ([child-type path-for study] (child-list-path child-type path-for study 1))
  ([child-type path-for study page-num]
   (let [list-handler (keyword (str "study-" (name child-type) "s-handler"))]
     (path-for list-handler :study-id (:study/id study) :page-num page-num))))

(defn child-action-path [child-type action path-for study]
  (let [find-handler (keyword (str (name action) "-" (name child-type) "-handler"))]
    (path-for find-handler :study-id (:study/id study))))

(defn- render-child-find-query [child-type path-for study]
  {:href (child-action-path child-type :find path-for study)
   :params {:id {:type 'Str}}})

(defn render-create-study-form [path-for]
  {:href (path-for :create-study-handler)
   :params
   {:id {:type 'Str :desc "The id has to be unique within the whole system."}
    :name {:type 'Str :desc "A short name for the study."}
    :desc {:type 'Str :desc "A free-text description of the study."}}})

(defn render-study-event-def-form [path-for study]
  {:href (child-action-path :study-event-def :create path-for study)
   :params
   {:id {:type 'Str}
    :name {:type 'Str}
    :desc {:type 'Str :optional true}}})

(defn render-form-def-form [path-for study]
  {:href (child-action-path :form-def :create path-for study)
   :params
   {:id {:type 'Str}
    :name {:type 'Str}
    :desc {:type 'Str :optional true}}})

(defn render-item-group-create-form [path-for study]
  {:href (child-action-path :item-group-def :create path-for study)
   :params
   {:id {:type 'Str}
    :name {:type 'Str}
    :desc {:type 'Str :optional true}}})

(defn render-item-def-form [path-for study]
  {:href (child-action-path :item-def :create path-for study)
   :params
   {:id {:type 'Str}
    :name {:type 'Str}
    :data-type {:type '(enum :text :integer)}
    :desc {:type 'Str :optional true}
    :question {:type 'Str :optional true}
    :length {:type 'Int :optional true}}})

(defnk render-study [study [:request path-for]]
  (-> {:id (:study/id study)
       :name (:study/name study)
       :desc (:study/desc study)}
      (assoc
        :links
        {:up {:href (path-for :service-document-handler)}
         :self {:href (study-path path-for study)}
         :lens/study-event-defs
         {:href (child-list-path :study-event-def path-for study)}
         :lens/form-defs
         {:href (child-list-path :form-def path-for study)}
         :lens/item-group-defs
         {:href (child-list-path :item-group-def path-for study)}
         :lens/item-defs
         {:href (child-list-path :item-def path-for study)}}

        :queries
        {:lens/find-study-event-def
         (render-child-find-query :study-event-def path-for study)

         :lens/find-form-def
         (render-child-find-query :form-def path-for study)

         :lens/find-item-group-def
         (render-child-find-query :item-group-def path-for study)

         :lens/find-item-def
         (render-child-find-query :item-def path-for study)}

        :forms
        {:lens/create-study-event-def
         (render-study-event-def-form path-for study)

         :lens/create-form-def
         (render-form-def-form path-for study)

         :lens/create-item-group-def
         (render-item-group-create-form path-for study)

         :lens/create-item-def
         (render-item-def-form path-for study)

         :lens/create-subject
         {:href (child-action-path :subject :create path-for study)
          :params
          {:id {:type 'Str}}}}

        :actions [:update :delete])))

(defnk exists-study? [db [:request [:params study-id]] :as ctx]
  (when-let [study (api/find-study db study-id)]
    (assoc ctx :study study)))

(def select-study-props (select-props :study :name :desc))

(def study-handler
  "Handler for GET, PUT and DELETE on a study.

  Implementation note on PUT:

  The resource compares the current ETag with the If-Match header based on a
  possibly old version of the study taken from a database outside of the
  transaction. The update transaction is than tried with name and desc from that
  possibly old study as reference. The transaction only succeeds if the name and
  desc are still the same on the in-transaction study."
  (resource
    (standard-entity-resource-defaults)

    :exists? exists-study?

    :processable? (entity-processable :name :desc)

    ;;TODO: simplyfy when https://github.com/clojure-liberator/liberator/issues/219 is closed
    :etag
    (fnk [representation {status 200} [:request path-for] :as ctx]
      (when (= 200 status)
        (let [study (:study ctx)]
          (md5 (str (:media-type representation)
                    (all-studies-path path-for)
                    (study-path path-for study)
                    (child-list-path :study-event-def path-for study)
                    (child-list-path :form-def path-for study)
                    (child-list-path :item-group-def path-for study)
                    (child-list-path :item-def path-for study)
                    (child-action-path :study-event-def :find path-for study)
                    (child-action-path :study-event-def :create path-for study)
                    (child-action-path :form-def :find path-for study)
                    (child-action-path :form-def :create path-for study)
                    (child-action-path :item-group-def :find path-for study)
                    (child-action-path :item-group-def :create path-for study)
                    (child-action-path :item-def :find path-for study)
                    (child-action-path :item-def :create path-for study)
                    (child-action-path :subject :create path-for study)
                    (:name study)
                    (:desc study))))))

    :put!
    (fnk [conn study new-entity]
      (let [new-entity (util/prefix-namespace :study new-entity)]
        {:update-error (api/update-study conn (:study/id study)
                                         (select-study-props study)
                                         (select-study-props new-entity))}))

    :delete!
    (fnk [conn study]
      (api/retract-entity conn (:db/id study)))

    :handle-ok render-study))

(defn render-embedded-study [path-for study]
  {:id (:study/id study)
   :name (:study/name study)
   :desc (:study/desc study)
   :links {:self {:href (study-path path-for study)}}})

(defn render-embedded-studies [path-for studies]
  (r/map #(render-embedded-study path-for %) studies))

(defn all-studies-handler [path-for]
  (resource
    (resource-defaults)

    :handle-ok
    (fnk [db [:request params]]
      (let [page-num (parse-page-num (:page-num params))
            filter (:filter params)
            studies (if (str/blank? filter)
                      (api/all-studies db)
                      (api/list-matching-studies db filter))
            next-page? (not (lr/empty? (paginate (inc page-num) studies)))
            path #(-> (all-studies-path path-for %)
                      (assoc-filter filter))]
        {:links
         (-> {:up {:href (path-for :service-document-handler)}
              :self {:href (path page-num)}}
             (assoc-prev page-num path)
             (assoc-next next-page? page-num path))
         :forms
         {:lens/create-study
          (render-create-study-form path-for)}
         :embedded
         {:lens/studies
          (->> (paginate page-num studies)
               (render-embedded-studies path-for)
               (into []))}}))))

(def create-study-handler
  (resource
    (standard-create-resource-defaults)

    :processable?
    (fnk [[:request params]]
      (or (and (:id params) (:name params) (:desc params))
          [false {:error (str ":id, :name or :desc missing in "
                              (keys params))}]))

    :post!
    (fnk [conn [:request params]]
      (if-let [study (api/create-study conn (:id params) (:name params)
                                       (:desc params))]
        {:study study}
        (throw (ex-info "Duplicate!" {:type :duplicate}))))

    :location (fnk [study [:request path-for]] (study-path path-for study))

    :handle-exception (duplicate-exception "Study exists already.")))

;; ---- For Childs ------------------------------------------------------------

(defn study-child-list-resource-defaults []
  (assoc
    (resource-defaults)

    :processable? (fnk [[:request params]] (:study-id params))

    :exists? exists-study?))

(defn exists-study-child?
  "Child types can be:

    * :study-event-def
    * :form-def
    * :item-group-def
    * :item-def"
  [child-type]
  (fnk [study [:request params]]
    (let [child-id (params (keyword (str (name child-type) "-id")))]
      (when-let [child (api/find-study-child study child-type child-id)]
        {:def child}))))

(defn child-path
  ([child-type path-for child]
   (let [study-key (keyword "study" (str "_" (name child-type) "s"))
         child-id-key (keyword (name child-type) "id")]
     (child-path child-type path-for (:study/id (study-key child))
                 (child-id-key child))))
  ([child-type path-for study-id child-id]
   (let [handler (keyword (str (name child-type) "-handler"))
         child-id-key (keyword (str (name child-type) "-id"))]
     (path-for handler :study-id study-id child-id-key child-id))))
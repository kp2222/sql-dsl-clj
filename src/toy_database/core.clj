(ns toy-database.core
  (:refer-clojure :exclude [or and]))

(def db (atom {}))

(defn insert-into
  "adds a new record"
  [table-name record]
  (swap! db update-in [table-name] conj record))

(defmacro def-filter
  [name args-vec pred-body]
  `(defn ~name
     ~args-vec
     (fn [~'%]
       ~pred-body)))

(def-filter eq
  [attr val]
  (= (attr %) val))

(def-filter gt
  [attr val]
  (pos? (compare (attr %) val)))

(def-filter and
  [& preds]
  ((apply every-pred preds) %))

(def-filter or
  [& preds]
  ((apply some-fn preds) %))

(defn where
  [pred]
  pred)

(defn select
  [table filter-pred]
  (filter filter-pred
          (table @db)))


(comment

  (reset! db {})
  
  @db

  (insert-into :songs {:title "Roses" :artist "Kathy Mattea" :rating 7})
  (insert-into :songs {:title "Fly" :artist "Dixie Chicks" :rating 8})
  (insert-into :songs {:title "Home" :artist "Dixie Chicks" :rating 9})
  (insert-into :songs {:title "Home" :artist "Dixie Chicks" :rating 9})  
  

  (select :songs (where (eq :title "Roses")))

  (select :songs (eq :title "Roses"))

  (select :songs (where (gt :rating 8)))

  (macroexpand '(def-filter eq
                  [attr val]
                  (= (attr-name record) attr-val)))


 (select :songs (where (or (eq :artist "Dixie Chicks")
                             (gt :rating 6))))

  
  
)



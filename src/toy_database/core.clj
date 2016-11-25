(ns toy-database.core
  (:refer-clojure :exclude [or and])
  (:require [clojure.pprint]))

(def db (atom {}))

(defn insert-into
  "adds a new record"
  [table-name record]
  (swap! db update-in [table-name] conj record))


(defmacro defpred
  [name args pred-fn]
  `(defn ~name
     ~args
     (fn[~'%]
       ~pred-fn)))

;; standard predicates
(defn eq
  [attr-name attr-val]
  (fn[r]
    (= (attr-name r) attr-val)))

(defn not-eq
  [attr-name attr-val]
  (comp not (eq attr-name attr-val)))

(defn bw
  [attr-name start end]
  (fn[r]
    (<= start (attr-name r) end)))

(defn and
  [& preds]
  (apply every-pred (flatten preds)))

(defn or
  [& preds]
  (apply some-fn (flatten preds)))


(defn where


  [& preds]
  (fn[r] ((and preds) r)))

(defn select
  [table selector-fn]
  (filter selector-fn
          (table @db)))


(comment

  (reset! db {})
  
  @db

  (insert-into :projects {:name "mGage" :programmers 2})
  (insert-into :projects {:name "Aconex" :programmers 4})
  (insert-into :projects {:name "GoodKarma" :programmers 3})
  (insert-into :projects {:name "intersect" :programmers 3})

  ;; Manually select records from the database using clojure collection related functions
  
  (insert-into :table-1 {:attribute "value" :the-one true})

  (select :table-1
          (where (eq :attribute "value")
                 (eq :the-one true)))

  ;; This seems nice but not very flexible the users might want to combine predicates in other ways. If we look at this very closely and predicate combiner is no different from normal predicates except for the fact that they take other predicates as argument. Can we define them just the way we defined eq

  (select :projects
          (where (and
                  (eq :name "mGage")
                  (eq :programmers 2))))

  ;; Now this does not seem to be working because union is expecting an array of preds
  ;; We will need to modify union to accept either an array of preds or a variable
  ;; number of predicates

  (select :projects (where (or
                            (eq :name "mGage")
                            (eq :programmers 3 ))))


  ;; Let's try some more predicates, now that we have eq we should perhaps create not-eq.

  (select :projects
          (where (or (eq :name "mGage")
                     (bw :programmers 2 4 ))))


  ;; Let's create a new predicate called between. This will test if an attribute value is between two values

  ;; creating new predicates is becoming a pain now because we have to deal with all the details about partial function et el. Let's try to clean this up.


  (macroexpand '(defpred a-eq
                  [attr-name attr-val]
                  (= (attr-name %) attr-val)))


  (defpred a-eq
    [attr-name attr-val]
    (= (attr-name %) attr-val))


  (select :projects
          (where (or (a-eq :name "mGage")
                     (bw :programmers 2 4 ))))


  ;; At this point we can create fairly useful select queris. We can still implement functionailty to select only a selected attributes and also support for aggregate functions that will come in the second part
  )



Clojure is a modern dialect of Lisp. Being a Lisp is what gives Clojure a lot of its power. Clojure, Lisps in general, lend itself to a bottom-up style of programming. The basic idea is to gradually build up on the abstractions provided by the language and libraries until we have abstractions that can be directly applied to problems in the domain. We can take this approach to an extent where these abstractions act as a custom programming language specific to the domain or a DSL.

A DSL which uses a subset of the syntax of the host language is known an internal or embedded DSL. This is particularly effective in Clojure because of the syntax of Clojure or rather the lack of it :)

The trigger behind this post is the book called [Practical common lisp ](http://www.gigamonkeys.com/book/). This example mostly extends upon the example from the third chapter in this book.

### An SQL query DSL

The complete source code for this post is available on [github](https://github.com/kp2222/sql-dsl-clj)

The Goal of this exercise to create a toy database and a query DSL. We will piggyback on the Clojure's immutable data structures for the database.

We will use a Global Map to store the records (which are again just plain Clojure maps). The database will not have a schema or support for persistence.

```clojure
(def db (atom {})

;; An example database
{:songs
 ({:title "Home", :artist "Dixie Chicks", :rating 9}
  {:title "Home", :artist "Dixie Chicks", :rating 9}
  {:title "Fly", :artist "Dixie Chicks", :rating 8}
  {:title "Roses", :artist "Kathy Mattea", :rating 7})}
```

We will need an API to insert data into the database. Since we are using Clojure's immutable data structures we can just the sequence function to insert data. But let's introduce some basic abstraction to keep things easier to change later.

```Clojure
(defn insert-into
  [table-name record]
  (swap! db update-in [table-name] conj record))
  
;; insert sample data

(insert-into :songs {:title "Roses" :artist "Kathy Mattea" :rating 7})
(insert-into :songs {:title "Fly" :artist "Dixie Chicks" :rating 8})
(insert-into :songs {:title "Home" :artist "Dixie Chicks" :rating 9})
(insert-into :songs {:title "Home" :artist "Dixie Chicks" :rating 9})  
  
  
```
We have now a database and an API to insert data into it. We can finally start on the querying API.

Let's start with the simplest solution that will work. Let's create a function "select" that will return all the records from a table.

```Clojure
(defn select
    [table-name]
    (table-name @db))
    
toy-database.core>  (select :songs)

({:title "Roses", :artist "Kathy Mattea", :rating 7}
 {:title "Fly", :artist "Dixie Chicks", :rating 8}
 {:title "Home", :artist "Dixie Chicks", :rating 9}
 {:title "Home", :artist "Dixie Chicks", :rating 9})
```


That works and it has a very vague similarity to SQL but not very useful  yet because the bare minimum a query language should support is the ability to filter the records based on search criteria.

Modifying the select method to add support for filtering records based on a predicate function.

```Clojure
(defn select
    [table-name where-pred]
    (filter where-pred
            (table-name @db)))
            
toy-database.core> (select :songs (fn[record] (= (:title record) "Roses")))
({:title "Roses", :artist "Kathy Mattea", :rating 7})
```

This does what we wanted but 

* This hardly looks like SQL 
* This API leaks the implementation of the db to the consumers of the API.

Let's see what are some quick improvements we can make. Let's take a look at the use case from the last example. 

```clojure
(select :songs (fn[record] (= (:title record) "Roses")))
```

The only things that will change in the above query are the attribute name (:title) and the value ("Roses"). Everything else is just an artifact of our  implementation. Let's see if we can change our API so that the consumer only has to supply the attribute name and the value.

Let's create a new function `where`, which takes the attribute name and value , then returns the  same selector function.

**Functions which take other functions as arguments and/or return other function are known as Higher order functions.**

```Clojure
 (defn where
    [attr-name attr-val]
    (fn[record]
      (= (attr-name record) attr-val)))

```

Select query with these changes.

```clojure
 (select :songs (where :title "Roses"))
```

This looks much better and it is starting to look more like an SQL query. 

At this point, we only support on kind of filtering of records that is based on the equality of a single attribute

Let's add support for more filtering methods and to do so we need to make it as easy as it is possible to create new filtering methods.

As a first step, we will extract the equality predicate from inside `where`

```Clojure
;; eq is a higher order function which accepts the attribute name
;; and attribute value and returns the actual predicate function
;; which will run against a record in the database

(defn eq
  [attr-name attr-val]
  (fn[r]
    (= (attr-name r) attr-val)))
 
```

Now that `eq`  implements the same functionality that `where` used to. should we just get rid of `where`?

```clojure
select :songs (eq :title "Roses")
```

Although this works just fine, I feel we have some lost some clarity in our API. The where method made it more explicit that a filter operation is underway. Let's add the where clause back

```clojure
(select :songs (where (eq :name "Roses")))
```

This looks better but now that `where`is just going to be there for aesthetic reasons what would it actually do? Ideally, it should delegate the actual work to the predicate function passed to it. Let's re-define `where` to do that 

```Clojure
(defn where
  [pred]
  (fn [r]
    (pred r)))
```

Looking at this closely it is easier to see that we can simplify this further

```Clojure
(defn where
  [pred]
  pred)

toy-database.core> (select :songs (where (eq :title "Roses")))
({:title "Roses", :artist "Kathy Mattea", :rating 7})

```

Let's add support for one more filter predicate. We will next add a `greater than` filter. This would enable us to write queries like fetch all the songs with a rating greater than 8. 

Let's name this new filter `gt`. We will use the compare method from Clojure core. 

*(compare x y) - Returns a negative number, zero, or a positive number
when x is logically 'less than', 'equal to', or 'greater than'
y*

```Clojure
(defn gt
  [attr-name v]
  (fn[r]
    (pos? (compare (attr-name r) v))))
```

Trying out our new filtering method in a select query

```Clojure
toy-database.core> (select :songs (where (gt :rating 8)))
({:title "Home", :artist "Dixie Chicks", :rating 9} {:title "Home", :artist "Dixie Chicks", :rating 9})
```
This looks nice and looks like we are on the right track here. Given a method to combine these filters we have a reasonably powerful querying language.

Before we get to implementing methods to combine predicates let's take a relook at how the current predicates are implemented.

```Clojure
(defn eq
  [attr-name attr-val]
  (fn[r]
    (= (attr-name r) attr-val)))

(defn gt
  [attr-name v]
  (fn[r]
    (pos? (compare (attr-name r) v))))
```

At this point, there is no explicit API for creating new filtering functions. Let's try to bring in a bit more structure so that it is is more explicit and hopefully a bit more concise.

For creating a new filter all we should have to do is:

* Specify the arguments that the filter takes
* The logic for rejecting or selecting a given record. 

Given the above, our code should generate the predicate method in a form similar to our current `eq` and `gt` methods.

Before we go ahead try to implement it let's see how we want this API to look like. The actual predicate logic that consumers of this API will write will need access to the record. Let's assume that this is somehow made available as `%`

```Clojure
(def-filter gt
    [attr val]
    (pos? (compare (attr %) val)))
```

Now we will see what it take to actually implement something like this. The simplest solution is to take the above form and generate code similar to the previous version of `eq` and `gt`. This where Clojure's macro system comes in handy. Let's create a macro which does this.

```Clojure
(defmacro def-filter
  [name args-vec pred-body]
  `(defn ~name
     ~args-vec
     (fn [~'%]
       ~pred-body)))

```
*Clojure goes to great extent to avoid the problem of variable capture in macros. Macros which deliberately tries to do variable capture are called [anaphoric macros](https://en.wikipedia.org/wiki/Anaphoric_macro). **In a production codebase, this is probably a very bad idea** but we will go ahead and use it anyways ( `~'%`) in this example because it makes the consumer API slightly  simpler*. 

Let's take a look at how to use this macro

```Clojure
(def-filter eq
  [attr val]
  (= (attr %) val))

;; This macro usage expands to a form very similar to our original
;; definition of eq

toy-database.core> (pprint (macroexpand '(def-filter eq
                  [attr val]
                  (= (attr-name record) attr-val))))
(def
 eq
 (clojure.core/fn
  ([attr val] (clojure.core/fn [%] (= (attr-name record) attr-val)))))
```

Now that we have a better way to create new filters. Let's add some constructs that will allow us to combine these filter predicates. We usually combine the predicates using a logical `and` or `or`

If we look closely at what these new constructs need to do you can see that they are not very different from the filters we have already created. These are just filters that will take other filters as arguments, Higher order filters if you want to call it that way :)

One way to combine filters is using logical and. The record gets selected into the result if all the filters select them. It turns out there is already a Clojure core function called every-pred which does what we want.

*every-pred Takes a set of predicates and returns a function f that returns true if all of its
composing predicates return a logical true value against all of its arguments, else it returns
false. Note that f is short-circuiting in that it will stop execution on the first
argument that triggers a logical false result against the original predicates.*

```Clojure
(def-filter and
  [& preds]
  ((apply every-pred preds) %))
```

Now that we are at this anyway let's implement `or` as well. Turns we can again count on clojure core functions. There is function called some-fn

```Clojure
(def-filter or
  [& preds]
  ((apply every-pred preds) %))
```

Let' see a sample query

```Clojure
(select :songs (where (or (eq :artist "Dixie Chicks")
                             (gt :rating 6))))
```

At this point, we have quite a powerful querying language that can be used to create queries that are arbitrarily complex. All this is made with just ~ 40 lines of code.

This is a listing of the complete source code up to this point

```Clojure
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
  [&amp; preds]
  ((apply every-pred preds) %))

(def-filter or
  [&amp; preds]
  ((apply some-fn preds) %))

(defn where
  [pred]
  pred)

(defn select
  [table filter-pred]
  (filter filter-pred
          (table @db)))

```

What next in part 2

* update statements
* support for joining multiple collections in queries
* support for aggregation grouping etc.

If you liked it so far, you should take a look at the following books/articles

* [Practical common lisp ](http://www.gigamonkeys.com/book/)
* [On Lisp - Paul Graham](http://www.paulgraham.com/onlisp.html)
* [Domain specific languages](http://martinfowler.com/books/dsl.html)
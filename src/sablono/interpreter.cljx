(ns sablono.interpreter
  (:require [clojure.string :refer [blank? join]]
            [sablono.util :refer [html-to-dom-attrs normalize-element]]))

(defprotocol IInterpreter
  (interpret [this] "Interpret a Clojure data structure as a React fn call."))

;; Taken from om, to hack around form elements.

#+cljs
(defn wrap-form-element [ctor react-name]
  (js/React.createClass
   #js
   {:displayName react-name
    :getInitialState
    (fn []
      (this-as this #js {:value (aget (.-props this) "value")}))
    :onChange
    (fn [e]
      (this-as
       this
       (let [handler (aget (.-props this) "onChange")]
         (when-not (nil? handler)
           (handler e)
           (.setState this #js {:value (.. e -target -value)})))))
    :componentWillReceiveProps
    (fn [new-props]
      (this-as this (.setState this #js {:value (aget new-props "value")})))
    :render
    (fn []
      (this-as
       this
       (.transferPropsTo
        this
        (ctor #js {:value (aget (.-state this) "value")
                   :onChange (aget this "onChange")
                   :children (aget (.-props this) "children")}))))}))

#+cljs
(def input (wrap-form-element js/React.DOM.input "OmInput"))

#+cljs
(def textarea (wrap-form-element js/React.DOM.textarea "OmTextarea"))

#+cljs
(def option (wrap-form-element js/React.DOM.option "OmOption"))

#+cljs
(defn dom-fn [tag]
  (if-let [dom-fn (aget js/React.DOM (name tag))]
    (get {:input sablono.interpreter/input
          :textarea sablono.interpreter/textarea}
         (keyword tag) dom-fn)
    (throw (ex-info (str "Unsupported HTML tag: " (name tag)) {:tag tag}))))

#+cljs
(defn attributes [attrs]
  (let [attrs (clj->js (html-to-dom-attrs attrs))
        class (join " " (flatten (seq (.-className attrs))))]
    (if-not (blank? class)
      (set! (.-className attrs) class))
    attrs))

#+cljs
(defn element
  "Render an element vector as a HTML element."
  [element]
  (let [[tag attrs content] (normalize-element element)]
    ((dom-fn tag)
     (attributes attrs)
     (cond
      (and (sequential? content)
           (string? (first content))
           (empty? (rest content)))
      (interpret (first content))
      content
      (interpret content)
      :else nil))))

(defn- interpret-seq [s]
  (into-array (map interpret s)))

#+cljs
(extend-protocol IInterpreter
  Cons
  (interpret [this]
    (interpret-seq this))
  ChunkedSeq
  (interpret [this]
    (interpret-seq this))
  LazySeq
  (interpret [this]
    (interpret-seq this))
  List
  (interpret [this]
    (interpret-seq this))
  IndexedSeq
  (interpret [this]
    (interpret-seq this))
  PersistentVector
  (interpret [this]
    (element this))
  default
  (interpret [this]
    this)
  nil
  (interpret [this]
    nil))

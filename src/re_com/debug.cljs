(ns re-com.debug
  (:require
    [goog.object            :as    gobj]
    [clojure.string         :as    string]
    [reagent.core           :as    r]
    [reagent.impl.component :as    component]
    [re-com.config          :refer [debug? root-url-for-compiler-output]]))

(defn short-component-name
  "Returns the interesting part of component-name"
  [component-name]
  ;; reagent.impl.component/component-name is used to obtain the component name, which returns
  ;; e.g. re_com.checkbox.checkbox. We are only interested in the last part.
  ;;
  ;; Also some components are form-2 or form-3 so will return -return from the anonymous render
  ;; function name. We keep the -render in the anonymous function name for JavaScript stack
  ;; traces for non-validation errors (i.e. exceptions), but we are not interested in that here.
  (-> component-name
      (string/split #"\.")
      (last)
      (string/replace #"_render" "")
      (string/replace #"_" "-")))

;(fn [& {:keys [child]}]
;  (let [old-child-args-as-map (apply hash-map (rest child))
;        old-ref-fn            (get-in old-child-args-as-map [:attr :ref])
;        ref-fn                (fn [el] (reset! element el) (when (fn? old-ref-fn) (old-ref-fn el)))
;        child-args-as-map     (assoc-in old-child-args-as-map [:attr :ref] ref-fn)
;        child-args-as-kw-args (reduce into [] (seq child-args-as-map))]
;    (into [(first child)] child-args-as-kw-args)))})))

(defn src->attr
  ;; TODO: delete this when migrated
  ([src]
   (let [component-name (component/component-name (r/current-component))]
     (js/console.warn component-name " not migrated to store args")
     (src->attr src component-name {})))
  ([src args]
   (src->attr src (component/component-name (r/current-component)) args))
  ([{:keys [file line] :as src} component-name args]
   (if debug? ;; This is in a separate `if` so Google Closure dead code elimination can run...
     (merge
       {:ref               (fn [el]
                             (if (nil? el)
                               (js/console.warn "ref in str->attr is nil")
                               (do
                                 (js/console.log "set args to" args)
                                 ;; Remember args so they can be logged later:
                                 (gobj/set el "__rc-args" (dissoc args
                                                                  :src
                                                                  :children))
                                 ;; User may have supplied their own ref like so: {:attr {:ref (fn ...)}}
                                 (when-let [ref-fn (get-in args [:attr :ref])]
                                   (when (fn? ref-fn)
                                     (ref-fn el))))))
        :data-rc-component (short-component-name component-name)}
       (when src
         {:data-rc-src     (str file ":" line)}))
     {})))

(defn component-stack
  ([el]
   (component-stack [] el))
  ([stack ^js/Element el]
   (if-not el ;; termination condition
     stack
     (let [component          (.. el -dataset -rcComponent)
           ^js/Element parent (.-parentElement el)]
       (->
         (if (= "component-stack-spy" component)
           stack
           (conj stack
                 {:el        el
                  :src       (.. el -dataset -rcSrc)
                  :component component}))
         (component-stack parent))))))

(defn validate-args-problems-style
  []
  ;; [IJ] TODO: take min-width, min-height, height, width, size etc from valid args if present; w/ a floor for min-width/min-height
  ;; [IJ] TODO: verify flexbox support in all cases.
  {:min-width      "32px"
   :min-height     "32px"
   :font-size      "1.4em"
   :text-align     "center"
   :vertical-align "center"
   :background      "#FF4136"})

(def h1-style "background: #FF4136; color: white; font-size: 1.4em; padding: 3px")
(def h2-style "background: #0074D9; color: white; padding: 0.25em")
(def code-style "font-family: monospace; font-weight: bold; background: #eee; color: #333; padding: 3px")
(def error-style "font-weight: bold")
(def index-style "font-weight: bold; font-size: 1.1em")

(def collision-icon "\uD83D\uDCA5")
(def gear-icon "⚙️") ;; the trailing 'space' is an intentional modifier, not an actual space, so do not delete it!
(def blue-book-icon "\uD83D\uDCD8")
(def question-mark-icon "❓")
(def exclamation-mark-icon "❗")
(def noncongruent-icon "≢")
(def stop-icon "\uD83D\uDE45\uD83C\uDFFD")
(def confused-icon "\uD83D\uDE15")
(def globe-icon "\uD83C\uDF10")

(defn log-component-stack
  [stack]
  (js/console.groupCollapsed (str "• %c Component stack (click me)") h2-style)
  (doseq [{:keys [i el component src]} (map-indexed #(assoc %2 :i (inc %1)) stack)]
    (let [args (gobj/get el "__rc-args")]
      #_(js/console.log args)
      (if component
        (if src
          (let [[file line] (string/split src #":")]
            (js/console.log
              (str "%c" i "%c " gear-icon " %c[" component " ...]%c in file %c" file "%c at line %c" line "%c\n      DOM: %o\n      Parameters: %O")
              index-style "" code-style "" code-style "" code-style "" el args))
          (js/console.log
            (str "%c" i "%c " gear-icon " %c[" component " ...]%c %o")
            index-style "" code-style "" el))
        (js/console.log (str "%c" i "%c " globe-icon " %o") index-style "" el))))
  (js/console.groupEnd))

(defn log-validate-args-error
  [element problems component-name {:keys [file line] :as src}]
  (let [source-url    (when root-url-for-compiler-output (str root-url-for-compiler-output file ":" line))]
    (js/console.group (str "%c" collision-icon " re-com validation error ") h1-style)
    (if src
      (if source-url
        (js/console.log
          (str "• " gear-icon "%c[" (short-component-name component-name) " ...]%c in file %c" file "%c at line %c" line "%c see " source-url)
          code-style "" code-style "" code-style "")
        (do
          (js/console.log
            (str "• " gear-icon "%c[" (short-component-name component-name) " ...]%c in file %c" file "%c at line %c" line)
            code-style "" code-style "" code-style)
          (js/console.log
            (str "• " blue-book-icon " Add %cre-com.config/root-url-for-compiler-output%c to your %c:closure-defines%c to enable clickable source urls")
            code-style "" code-style "")))
      (do
        (js/console.log
          (str "• " gear-icon "%c[" (short-component-name component-name) " ...]")
          code-style)
        (js/console.log (str "• " blue-book-icon " Learn how to add source coordinates to your components at https://re-com.day8.com.au/#/debug"))))
    (doseq [{:keys [problem arg-name expected actual validate-fn-result]} problems]
      (case problem
        ;; [IJ] TODO: :validate-fn-return
        :unknown         (js/console.log
                           (str "• " question-mark-icon " %cUnknown parameter: %c" arg-name)
                           error-style code-style)
        :required        (js/console.log
                           (str "• " exclamation-mark-icon "  %cMissing required parameter: %c" arg-name)
                           error-style code-style)
        :validate-fn     (js/console.log
                           (str "• " noncongruent-icon "  %cParameter %c" arg-name "%c expected %c" (:type expected ) "%c but got %c" actual)
                           error-style code-style error-style code-style error-style code-style)
        :validate-fn-map (js/console.log
                           (str "• " stop-icon " %c" (:message validate-fn-result))
                           error-style)
        (js/console.log "• " confused-icon " Unknown problem reported")))
    (log-component-stack (component-stack @element))
    (js/console.groupEnd)))

(defn validate-args-error
  [& {:keys [problems component-name src]}]
  (let [element                 (atom nil)
        internal-problems       (atom problems)
        internal-component-name (atom component-name)
        internal-src            (atom src)]
    (r/create-class
      {:display-name "validate-args-error"

       :component-did-mount
       (fn [this]
         (log-validate-args-error element @internal-problems @internal-component-name @internal-src))

       :component-did-update
       (fn [this argv old-state snapshot]
         (log-validate-args-error element @internal-problems @internal-component-name @internal-src))

       :reagent-render
       (fn [& {:keys [problems component-name src]}]
         (reset! internal-problems problems)
         (reset! internal-component-name component-name)
         (reset! internal-src src)
         [:div
          (merge
            {:title    "re-com validation error. Look in the devtools console."
             :ref      (fn [el] (reset! element el))
             :style    (validate-args-problems-style)}
            (src->attr src component-name))
          collision-icon])})))

(defn component-stack-spy
  [& {:keys [child src]}]
  (let [element (atom nil)
        log-fn  (fn []
                  (let [el @element]
                    (when el
                      (let [first-child (first (.-children el))]
                        (js/console.group "%c[component-stack-spy ...]%c" code-style "")
                        (log-component-stack (component-stack first-child))
                        (js/console.groupEnd)))))]
    (r/create-class
      {:display-name         "component-stack-spy"
       :component-did-mount  log-fn
       :component-did-update log-fn
       :reagent-render
       (fn [& {:keys [child src]}]
         [:div
          (src->attr src {:attr {:ref (fn [el] (reset! element el))}})
          child])})))

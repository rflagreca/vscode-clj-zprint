(ns vscode-clj-zprint.core
  (:require ["vscode" :as vscode]
            ["fs" :as fs]
            [zprint.core :as zprint]
            [cljs.reader :as reader]))

(defn load-config
  []
  (reader/read-string
   (.readFileSync fs (str (js* "__dirname") "/.zprintrc") "utf8")))

(deftype ClojureDocumentRangeFormattingEditProvider [config]
 Object
   (provideDocumentRangeFormattingEdits [_ ^js document range options token]
     (let [text      (.getText document range)
           formatted (try (zprint/zprint-file-str text "" config)
                          (catch js/Error e (js/console.log (.-message e))))]
       (when formatted #js [(vscode/TextEdit.replace range formatted)]))))

(defn register-disposable
  [^js context ^js disposable]
  (-> (.-subscriptions context)
      (.push disposable)))

(defn dispose
  [^js context & disposables]
  (doseq [disposable disposables] (register-disposable context disposable)))

(def document-selector #js {:language "clojure"})

(defn activate
  [^js context]
  (let [config   (load-config)
        provider (ClojureDocumentRangeFormattingEditProvider. config)]
    (dispose context
             (vscode/languages.registerDocumentRangeFormattingEditProvider
              document-selector
              provider))))
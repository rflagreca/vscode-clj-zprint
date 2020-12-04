(ns vscode-clj-zprint.core
  (:require ["vscode" :as vscode]
            ["fs" :as fs]
            ["os" :as os]
            [zprint.core :as zprint]
            [cljs.reader :as reader]))

(defn slurp
  [file]
  (->
    (.readFileSync fs file "utf8")
    (.toString)))

(defn get-zprintrc-file-str
  []
  (let [home-str (.homedir os)
        workspace-zprintrc-file-str (str (.. (first vscode/workspace.workspaceFolders) -uri -fsPath) "/.zprintrc")
        global-zprintrc-file-str (str home-str "/.zprintrc")]
    (cond
      (.existsSync fs workspace-zprintrc-file-str) workspace-zprintrc-file-str
      (.existsSync fs global-zprintrc-file-str) global-zprintrc-file-str
      :else nil)))

(defn get-config!
  []
  (let [zprintrc-file-str (get-zprintrc-file-str)]
    (try
      (when zprintrc-file-str
        (let [zprintrc-str (slurp zprintrc-file-str)]
          (when zprintrc-str (reader/read-string zprintrc-str))))
      (catch :default _ {}))))

(deftype ClojureDocumentRangeFormattingEditProvider [config]
 Object
   (provideDocumentRangeFormattingEdits [_ ^js document range options token]
     (let [text       (.getText document range)
           path vscode/window.activeTextEditor.document.uri.fsPath
           formatted (try (zprint/zprint-file-str text path config)
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
  (let [config   (get-config!)
        provider (ClojureDocumentRangeFormattingEditProvider. config)]
    (dispose context
             (vscode/languages.registerDocumentRangeFormattingEditProvider
              document-selector
              provider))))
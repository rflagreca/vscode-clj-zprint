(ns vscode-clj-zprint.core
  (:require ["vscode" :as vscode]
            ["fs" :as fs]
            ["os" :as os]
            [zprint.core :as zprint]
            [zprint.config]
            [clojure.string]))

(def channel (vscode/window.createOutputChannel "vscode-clj-zprint Messages"))

(defn output-message "Put a string into the channel." [message] (.appendLine channel message) nil)

(defn slurp
  [file]
  (-> (.readFileSync fs file "utf8")
      (.toString)))

(def zprintrc ".zprintrc")
(def zprintedn ".zprint.edn")

(defn get-config-from-file
  "Read in an options map from a single file.  Returns [options-from-file
  error-string filepath file-exists], where file-exists is non-nil
  if we could open the file.  This allows us to distinguish between
  file not found and errors reading the file."
  [filepath]
  (when filepath
    (let [[file-str file-error]
            (try [(slurp filepath) nil]
                 (catch :default e
                   [nil (str "Unable to open configuration file " filepath " because " e)]))]
      (if file-error
        [nil file-error filepath nil]
        (try (let [opts-file (zprint.config/sci-load-string file-str)]
               [opts-file nil filepath true])
             (catch :default e
               [nil (str "Unable to read configuration from file " filepath " because " e) filepath
                true]))))))

(defn find-found
  "Find first file that was found.  Accepts a sequence of vectors
  where the 4th element in the vectors is true if the file was
  found."
  [config-vec]
  (reduce #(when (nth %2 3) (reduced %2)) config-vec))

(defn get-config-from-path
  "Take a vector of filenames, and look in exactly one directory
  for all of the filenames.  Return the [option-map error-str
  full-file-path] from get-config-from-file for the first one found,
  nil if none were found and they were optional, or an error
  referencing all of them if none were found and they weren't
  optional."
  [filename-vec file-sep dir-vec optional]
  (let [dirspec (apply str (interpose file-sep dir-vec))
        config-vec (reduce #(let [config (get-config-from-file %2)]
                              (if (nth config 3) (reduced (conj %1 config)) (conj %1 config)))
                     []
                     (map (partial str dirspec file-sep) filename-vec))
        found (find-found config-vec)]
    (cond optional found
          found found
          :else [nil (str "Unable to load configuration from: " (mapv #(nth % 2) config-vec))
                 nil])))

(defn get-config-from-dirs
  "Take a vector of directories dir-vec and check for all of the
  filenames in filename-vec in the directories specified by dir-vec.
  When one is found, return (using reduced) the [option-map error-str
  full-file-path] from get-config-from-file, or nil if none are
  found.  Will now accept fns from any of the files since using
  sci/eval-string."
  [filename-vec file-sep home-zprint-dir-vec dir-vec _]
  (if (= home-zprint-dir-vec dir-vec)
    ; Stop when we are about to do the home directory
    (reduced nil)
    (let [config-vec (get-config-from-path filename-vec file-sep dir-vec :optional)]
      (if config-vec
        ; Got it!
        (reduced config-vec)
        ; Try again, with one less directory in dir-vec
        (into [] (butlast dir-vec))))))

(defn scan-up-dir-tree
  "Take a vector of filenames and scan up the directory tree from
  the workspace-str to the root, looking for any of the files
  in each directory.  Look for them in the order given in the vector.
  Return nil or a vector from get-config-from-file: [option-map
  error-str full-file-path] if one was found."
  [filename-vec file-sep workspace-str home-zprint-file-path]
  (let [; split either unix or windows file separators
        ; theory is that we can always access files with unix
        ; file separators, but we may get a path with either
        ; and the fsPath for the workspace will have os specific
        ; path separators according to the API
        dirs-to-root (clojure.string/split workspace-str #"\\|/")
        home-zprint-dir-vec (butlast (clojure.string/split home-zprint-file-path #"\\|/"))]
    ; The reduce will run the fn once for each directory, and it
    ; will trim one off each time it runs
    (reduce (partial get-config-from-dirs filename-vec file-sep home-zprint-dir-vec)
      dirs-to-root
      dirs-to-root)))

;!zprint {:fn-map {"str" :wrap}}
(defn handle-errors-and-set-options!
  "If we have options, do a zprint/set-options! and if it fails 
  (probably due to an incorrect options map key), output the 
  message to the extension's channel and continue. 
  If there are errors, output those to the extensions channel.
  Returns options when it works, nil otherwise."
  [options errors filepath initial because]
  (cond options (try (zprint/set-options! options)
                     (output-message (str "Successfully loaded" (when initial (str " " initial " "))
                                       "configuration options from '" filepath "'"
                                       (when because (str " because " because)) "."))
                     options
                     (catch :default e
                       (output-message (str "Unable to successfully set-options! on options from '"
                                         filepath "' because: " e))))
        errors (output-message errors)))

(defn configure-home-zprint
  "Configure the .zprintrc or .zprint.edn from the users home directory.
  Return the filepath of the file, if any."
  []
  (let [home (.homedir os)
        [options errors filepath] (when home
                                    (get-config-from-path [zprintrc zprintedn] "/" [home] nil))]
    (handle-errors-and-set-options! options errors filepath "initial" nil)
    filepath))

(defn get-workspace-str
  "Find the current (or at least a current) workspace string.
  Return nil if no current workspace."
  []
  (let [workspace (first vscode/workspace.workspaceFolders)]
    (when workspace (.. ^js workspace -uri -fsPath))))

(defn get-config!
  "There are three steps in this process.  First, we attempt to
  configure zprint from the ~/.zprintrc or ~/.zprint.edn in the
  users home directory.  We log a message however that comes out.
  Then, we look at :search-config? to see if we should look for
  either .zprintrc or .zprint.edn in the current working directory
  and above that unti we find a file.  Of course, we don't really
  have a current working directory.  So we will use the first of
  the workspaces we find, if any.  Then if :search-config? was
  false, we check :cwd-zprintrc?.  If it is true, we look for
  .zprintrc or .zprint.edn in the current workspace."
  []
  (let [home-zprint-path (configure-home-zprint)
        options (zprint.config/get-options)
        cwd-zprintrc? (:cwd-zprintrc? options)
        search-config? (:search-config? options)
        need-workspace? (or cwd-zprintrc? search-config?)]
    (when need-workspace?
      (let [workspace-str (get-workspace-str)]
        (if workspace-str
          (cond
            search-config?
              (let [[options errors filepath]
                      (scan-up-dir-tree [zprintrc zprintedn] "/" workspace-str home-zprint-path)]
                (handle-errors-and-set-options! options
                                                errors
                                                filepath
                                                "additional"
                                                ":search-config? was true"))
            cwd-zprintrc?
              (let [[options errors filepath]
                      (get-config-from-path [zprintrc zprintedn] "/" [workspace-str] :optional)]
                (handle-errors-and-set-options! options
                                                errors
                                                filepath
                                                "additional"
                                                ":cwd-zprintrc? was true")))
          (output-message
            (str "No current workspace found, so cannot load additional configuration based on "
              (cond search-config? "{:search-config? true}"
                    cwd-zprintrc? "{:cwd-zprintrc? true}"))))))))

(defn zprint-range->vscode-range
  "Given a range of lines from zprint and the document from which
  they were drawn, create a vscode range where the start is the
  range that zprint reported formatting and the character position
  of the start is zero.  The line number of the end is the line
  number zprint formatted, and the character position is whatever
  the rangeIncludingLineBreak uses.  Well, really, the
  rangeIncludingLineBreak appears to be one larger than the line
  number of the line from which it came, but that must be how
  vscode handles line breaks."
  [^js document start end]
  (let [start-position (new vscode/Position start 0)
        end-line (.lineAt document end)
        end-position-ilb end-line.rangeIncludingLineBreak.end]
    (new vscode/Range start-position end-position-ilb)))

;!zprint {:fn-map {"str" :wrap}}
(deftype ^js ClojureDocumentRangeFormattingEditProvider []
  Object
    (provideDocumentRangeFormattingEdits [_ ^js document range options token]
      (let [text (.getText document)
            start-line range.start.line
            end-line range.end.line
            ; The API docs say not to use fsPath for display purposes
            ; so we will use path instead
            path vscode/window.activeTextEditor.document.uri.path
            [zprint-range formatted]
              (try
                (zprint/zprint-file-str text
                                        path
                                        {:input {:range {:start start-line, :end end-line}},
                                         :output {:range? true}})
                (catch js/Error e (output-message (str "Unable to format text: " (.-message e)))))]
        (when zprint-range
          (let [actual-start (:actual-start (:range zprint-range))
                actual-end (:actual-end (:range zprint-range))
                range-to-replace (when formatted
                                   (zprint-range->vscode-range document actual-start actual-end))]
            (output-message
              (if formatted
                (str "Successfully formatted. Selected/Actual/Replaced:[" start-line "," end-line
                  "]/[" actual-start "," actual-end "]/[(" range-to-replace.start.line ","
                  range-to-replace.start.character "),(" range-to-replace.end.line ","
                  range-to-replace.end.character ")] in file: '" path "'")
                (str "No format performed. Selected/Actual:[" start-line "," end-line "]/["
                  actual-start "," actual-end "] in file: '" path "'")))
            (when formatted #js [(vscode/TextEdit.replace range-to-replace formatted)]))))))

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
  (get-config!)
  (let [provider (ClojureDocumentRangeFormattingEditProvider.)]
    (dispose context
             (vscode/languages.registerDocumentRangeFormattingEditProvider document-selector
                                                                           provider))))
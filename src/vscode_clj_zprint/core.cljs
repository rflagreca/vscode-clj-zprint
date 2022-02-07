(ns vscode-clj-zprint.core
  (:require ["vscode" :as vscode]
            ["fs" :as fs]
            ["os" :as os]
            [zprint.core :as zprint]
            [zprint.config]
            [clojure.string]
            [clojure.edn]))

(def channel (vscode/window.createOutputChannel "vscode-clj-zprint Messages"))

; Data storage for raw configuration information from vscode

(def config-map-save (atom {}))

(def vscode-options-map (atom {}))
(def configured? (atom false))

; Message Handling Functions

(defn output-message
  "Put a string into the channel.  Returns nil."
  [message]
  (.appendLine channel message)
  nil)

(defn error-message
  "Output an error message popup as well as a message into the channel.
   es is the value for showErrorMessage, and os (if it appears) is the
   string for output-message.  If there is no os, then es is used.
   Returns non-nil."
  ([error-str output-str]
   (vscode/window.showErrorMessage error-str)
   (output-message output-str)
   error-str)
  ([s] (error-message s s)))

;!zprint {:format :next :fn-map {"str" :wrap}}
(defn handle-errors-and-set-options!
  "If we have options, do a zprint/set-options! and if it fails 
  (probably due to an incorrect options map key), output the 
  message to the extension's channel and continue. 
  If there are errors, output those to the extensions channel.
  Returns nil when it succeeds, non-nil for failure."
  [options errors filepath remove-style? initial because]
  (cond options (try
                  (zprint/set-options! (if remove-style? (dissoc options :style) options) filepath)
                  (output-message (str "Successfully loaded" (when initial (str " " initial " "))
                                    "configuration options from '" filepath "'"
                                    (when because (str " because " because)) "."))
                  (catch :default e
                    (error-message (str e)
                                   (str "Unable to successfully set-options! on options from '"
                                     filepath "' because: " e))))
        errors (error-message errors)))

; Utility Functions

(defn concat-errors
  "Given a vector containings strings or nil, produce a string
  with the non-empty strings separated by semicolons, and an
  introduction as to how many errors were found if there were
  more than one.  If none of the strings are not empty, return 
  nil."
  [str-vec]
  (let [no-nil-seq (remove nil? str-vec)
        no-empty-seq (remove empty? no-nil-seq)
        comma-seq (interpose "; " no-empty-seq)
        error-count (count no-nil-seq)]
    (cond (zero? error-count) nil
          (= error-count 1) (apply str comma-seq)
          :else (str "Found the following " error-count
                     " distinct errors: " (apply str comma-seq)))))

(defn number-or-nil
  "Given a string value, if the string is empty return the value
   as nil, if it is non-empty read it as a number.  Returns
   [value errors]"
  [s field-name]
  (let [n (clojure.edn/read-string s)
        errors (if (or (nil? n) (number? n))
                 nil
                 (str "The value of '"
                      field-name
                      "' must be a number or empty, instead found: '"
                      n
                      "'."))
        n (when (not errors) n)]
    [n errors]))

; External Configuration Functions

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
  nil if none were found and they were optional, or an warning
  is output directly referencing all of them if none were found 
  and they weren't optional.  No error is returned in this case,
  since this is only a warning."
  [filename-vec file-sep dir-vec optional]
  (let [dirspec (apply str (interpose file-sep dir-vec))
        config-vec (reduce #(let [config (get-config-from-file %2)]
                              (if (nth config 3) (reduced (conj %1 config)) (conj %1 config)))
                     []
                     (map (partial str dirspec file-sep) filename-vec))
        found (find-found config-vec)]
    (cond optional found
          found found
          :else (do (output-message (str "Unable to load external configuration. Files not found: " 
                                         (mapv #(nth % 2) config-vec)))
                    [nil nil nil]))))

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
  (let [; Split on either unix or windows file separators.
        ; The theory is that we can always access files with unix
        ; file separators, but we may get a path with either.
        ; The fsPath for the workspace will have os specific
        ; path separators according to the API
        dirs-to-root (clojure.string/split workspace-str #"\\|/")
        home-zprint-dir-vec (butlast (clojure.string/split home-zprint-file-path #"\\|/"))]
    ; The reduce will run the fn once for each directory, and it
    ; will trim one off each time it runs
    (reduce (partial get-config-from-dirs filename-vec file-sep home-zprint-dir-vec)
      dirs-to-root
      dirs-to-root)))

(defn configure-home-zprint
  "Configure the .zprintrc or .zprint.edn from the users home directory.
  Return the filepath of the file, if any."
  [remove-style?]
  (let [home (.homedir os)
        [options errors filepath] (when home
                                    (get-config-from-path [zprintrc zprintedn] "/" [home] nil))]
    [filepath
     (handle-errors-and-set-options! options errors filepath remove-style? "initial" nil)]))

(defn get-workspace-str
  "Find the current (or at least a current) workspace string.
  Return nil if no current workspace."
  []
  (let [workspace (first vscode/workspace.workspaceFolders)]
    (when workspace (.. ^js workspace -uri -fsPath))))

(defn get-external-config!
  "There are three steps in this process.  First, we attempt to
  configure zprint from the ~/.zprintrc or ~/.zprint.edn in the
  users home directory.  We log a message however that comes out.
  Then, we look at :search-config? to see if we should look for
  either .zprintrc or .zprint.edn in the current working directory
  and above that unti we find a file.  Of course, we don't really
  have a current working directory.  So we will use the first of
  the workspaces we find, if any.  Then if :search-config? was
  false, we check :cwd-zprintrc?.  If it is true, we look for
  .zprintrc or .zprint.edn in the current workspace. If remove-style?
  is true, we remove any styles from any configuration files we
  find before we do the set-options! for them.  Returns nil for
  success, non-nil for failure."
  [remove-style?]
  (let [[home-zprint-path home-zprint-errors] (configure-home-zprint remove-style?)
        options (zprint.config/get-options)
        cwd-zprintrc? (:cwd-zprintrc? options)
        search-config? (:search-config? options)
        need-workspace? (or cwd-zprintrc? search-config?)]
    (if need-workspace?
      (let [workspace-str (get-workspace-str)]
        (if workspace-str
          (cond
            search-config?
              (let [[options errors filepath]
                      (scan-up-dir-tree [zprintrc zprintedn] "/" workspace-str home-zprint-path)]
                (or (handle-errors-and-set-options! options
                                                    errors
                                                    filepath
                                                    remove-style?
                                                    "additional"
                                                    ":search-config? was true")
                    home-zprint-errors))
            cwd-zprintrc?
              (let [[options errors filepath]
                      (get-config-from-path [zprintrc zprintedn] "/" [workspace-str] :optional)]
                (or (handle-errors-and-set-options! options
                                                    errors
                                                    filepath
                                                    remove-style?
                                                    "additional"
                                                    ":cwd-zprintrc? was true")
                    home-zprint-errors)))
          ; There may be other errors, but we only need to return one if there
          ; are any since it is only that there is one, not the specific error
          ; that matters.
          (error-message
            (str "No current workspace found, so cannot load additional configuration based on "
                 (cond search-config? "{:search-config? true}"
                       cwd-zprintrc? "{:cwd-zprintrc? true}")))))
      ; We didn't need a workspace
      home-zprint-errors)))

; VS Code configuration functions

(defn create-style-vec
  "Given a string with a comma separated set of styles, create
   a vector with each style as a separate keyword. Returns
   [style-vec errors] where only one of them is non-nil."
  [style-str]
  (let [split-vec (clojure.string/split style-str #"\,")
        replaced (apply str (interpose " " split-vec))
        style-vec-str (str "[" replaced "]")
        keyword-vec (clojure.edn/read-string style-vec-str)]
    (if (some false? (mapv keyword? keyword-vec))
      [nil (str "Some of these styles in the 'Array of Styles' are not keywords: '" style-str "'")]
      [keyword-vec nil])))

(defn build-vscode-options-map
  "Given the config-map, create and save a new vscode-options-map.
  Note that this does not save the config-map, only the newly
  constructed vscode-options-map if there are no errors.  It
  also does not do the set-options! for the newly constructed map.
  Returns [new-map errors].  If there are any errors, perhaps in
  handling the array of styles, reading the options map, or converting
  the width into a string, return the errors and the new-map as
  nil."
  [config-map]
  (let [[keyword-vec style-errors] (create-style-vec (:array-of-styles config-map))
        remove-style? (:use-only-these-styles config-map)
        ; No community formatting if they checked "Use Only These Styles"
        community-formatting? (when (not remove-style?) (:community-formatting config-map))
        [width width-errors] (number-or-nil (:width config-map) "Width")
        [options-map options-errors]
          (try (let [opts-map (zprint.config/sci-load-string (:options-map config-map))]
                 [opts-map nil])
               (catch :default e
                 [nil
                  (str "Unable to read VS Code config entry 'Options Map' because '"
                       (or (ex-message e) e)
                       "'")]))
        errors (concat-errors [style-errors width-errors options-errors])]
    (if errors
      (do
        ; If we have VS Code errors, then we aren't going to have a new
        ; vscode-options-map, so we need to forget the existing one!
        (reset! vscode-options-map {})
        [nil (error-message errors)])
      (let [style-vec (into [] (concat (if community-formatting? [:community] []) keyword-vec))
            explicit-map {:style style-vec}
            ; Only the use :width if it is not nil.
            explicit-map (if width (assoc explicit-map :width width) explicit-map)
            ; If they checked "Use Only These Styles" then the only styles they
            ; get are the styles in the arrar-of-styles.
            options-map (if remove-style? (dissoc options-map :style) options-map)
            new-map (zprint.config/merge-deep explicit-map options-map)]
        (reset! vscode-options-map new-map)
        [new-map nil]))))

(defn create-vscode-options-map
  "If the configuration has changed, then create an updated
  configurations options map. Any errors from creating the map will
  be output, and no map will be returned.  
  Returns [new-map config-map remove-style? ignore-external-files? errors]"
  []
  (let [configuration (vscode/workspace.getConfiguration "vscode-clj-zprint")
        ; Entire configuration as map, not a valid options map
        ; Data types are ???
        config-map {:width configuration.width,
                    :array-of-styles configuration.Styles.ArrayOfStyles,
                    :options-map configuration.OptionsMap,
                    :use-only-these-styles configuration.Styles.UseOnlyTheseStyles,
                    :community-formatting configuration.CommunityFormatting,
                    :ignore-external-files configuration.IgnoreExternalFiles}
        remove-style? (:use-only-these-styles config-map)
        ignore-external-files? (:ignore-external-files config-map)]
    ; Have any of the configuration elements changed since we last came through?
    (if (not= config-map @config-map-save)
      ; Things have changed, build a new options map
      (let [[new-map errors] (build-vscode-options-map config-map)]
        ; If we got a new map, then it was updated and so we should also update the
        ; raw data.
        [new-map config-map remove-style? ignore-external-files? errors])
      [nil config-map remove-style? ignore-external-files? nil])))

; One approach would be to hold the VS Code config as a options map outside of
; zprint's set-options! and send it in on every zprint-file-str call.  Then,
; when it changed, we wouldn't have to go back and remove the entire zprint
; configuration and reload the option files.  Except that the VS Code config
; says a lot about what we need to do with the zprint configuration read in
; from the .zprintrc or .zprint.edn files.  So we need to start from scratch
; every time the VS Code configuration changes.  Or at least when most of the
; VS Code configuration elements change.  Also, placing the VS Code config
; into zprint by using set-option! means that we can see what was actually
; configured by using the "Zprint: Output Current non-Default Configuration"
; command.  This command can be a huge help in trying to figure out what is
; actually being given to zprint when complex configuration is in play.

(defn update-configuration-if-needed
  "Update the zprint configuration from the VS Code configuration element."
  ([force-update?]
   (let [[new-map config-map remove-style? ignore-external-files? vscode-errors]
           (create-vscode-options-map)]
     ; Do this if we have a vscode config change or if someone forces us to do it anyway
     (when (or new-map vscode-errors force-update?)
       ; Remove current configuration
       (zprint.config/config-configure-all!)
       (when @configured? (output-message "Removed existing zprint configuration."))
       (let [external-errors (if ignore-external-files?
                               (output-message
                                 "Configured to ignore external .zprintrc and .zprint.edn files.")
                               (get-external-config! remove-style?))]
         (try (zprint/set-options! @vscode-options-map "VS Code config")
              (when (and new-map @configured?) (output-message "Detected change in VS Code config."))
              (output-message "Successfully updated zprint options map with VS Code config.")
              (reset! configured? true)
              ; Since the set-options! above worked, we can save the configuration
              ; (i.e., the config-map) so we don't try to do it again next time.
              ; If it doesn't work, we don't save the config-map so that each time
              ; we compare the current VS Code config and the saved config-map they
              ; look different, so that we will keep trying (and generating
              ; showErrorMessages to the user) so that they eventually correct the
              ; problem.  Or at least they know that something isn't right.
              ;
              ; If we didn't have any external errors, then if we get here we
              ; have also not had any internal configuration errors, so we can
              ; save the config-map.   This means that if we have any external
              ; or internal errors of any sort, we will continually try to set
              ; all of the configuration and display all of the errors each and
              ; every time.
              (when (not (or external-errors vscode-errors)) (reset! config-map-save config-map))
              (catch :default e (error-message (str e))))))))
  ([] (update-configuration-if-needed nil)))

(defn zprint-range->vscode-range
  "Given a range of lines from zprint and the document from which
  they were drawn, create a VS Code range where the start is the
  range that zprint reported formatting and the character position
  of the start is zero.  The line number of the end is the line
  number zprint formatted, and the character position is whatever
  the rangeIncludingLineBreak uses.  Well, really, the
  rangeIncludingLineBreak appears to be one larger than the line
  number of the line from which it came, but that must be how VS Code
  handles line breaks."
  [^js document start end]
  (let [start-position (new vscode/Position start 0)
        end-line (.lineAt document end)
        end-position-ilb end-line.rangeIncludingLineBreak.end]
    (new vscode/Range start-position end-position-ilb)))

;!zprint {:fn-map {"str" :wrap}}
#_{:clj-kondo/ignore [:unused-binding :clojure-lsp/unused-public-var]}
(deftype ^js ClojureDocumentRangeFormattingEditProvider []
  Object
    (provideDocumentRangeFormattingEdits [_ ^js document range options token]
      (update-configuration-if-needed)
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
                (catch js/Error e (error-message (str "Unable to format text: " (.-message e)))))]
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

(defn refresh-command-handler
  "Refresh the zprint configuration, presumably because it
   may have changed externally to VS Code."
  []
  (output-message (str "Zprint: Refresh Configuration"))
  ; Force complete configuration update
  (update-configuration-if-needed :force-update))

(defn current-config-command-handler
  "Output the current non-default configuration information"
  []
  (update-configuration-if-needed)
  (output-message (str "Zprint: Output Current non-Default Configuration\n"
                    (zprint/zprint-str (zprint.config/get-explained-set-options)))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn activate
  [^js context]
  (update-configuration-if-needed :force-update)
  (let [provider (ClojureDocumentRangeFormattingEditProvider.)
        refresh-command "vscode-clj-zprint.refresh"
        current-config-command "vscode-clj-zprint.currentconfig"]
    (dispose
      context
      (vscode/languages.registerDocumentRangeFormattingEditProvider document-selector provider)
      (vscode/commands.registerCommand refresh-command refresh-command-handler)
      (vscode/commands.registerCommand current-config-command current-config-command-handler))))


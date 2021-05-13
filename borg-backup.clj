#!/????/bb

(require '[clojure.edn :as edn]
         '[clojure.pprint :as pp]
         '[clojure.java.io :as io]
         '[clojure.tools.cli :as cli]
         '[clojure.java.shell :refer [sh]]
         '[clojure.stacktrace :as stacktrace])

(def home-dir "/Users/???")
(def borg-cmd (str home-dir "/dev/proggies/BorgBackup-1/borg-macosx64"))
(def borg-repo-dir-name "borg-backups")
(def borg-repo-local-test-path (str home-dir "/Documents/temp/borg-testing/" borg-repo-dir-name))
(def borg-repo-local-prod-path (str home-dir "/" borg-repo-dir-name))

(def chicago-tz (java.time.ZoneId/of "America/Chicago"))
(def date-time-fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH-mm-ss"))

(defn fmt-for-console [{:keys [exit out err]}sh-return-map]
  (str
   "exit code: " exit
   "\n-----------------\n"
   "out:\n" out
   "\n-----------------\n"
   "error:\n" err
   "\n-----------------\n"))

(def borg-working-dir (str home-dir "/.borg"))
(def logs-dir (str borg-working-dir "/logs"))
(def logs-dir-f (io/file logs-dir))

(def lock-fpath (str borg-working-dir "/LOCK"))
(def lock-f (io/file lock-fpath))

(def run-interval (java.time.Duration/ofDays 1))

(defn time->file-nameable-str [zoned-datetime]
  (.format zoned-datetime date-time-fmt))

(defn time->parsable-str [zoned-datetime]
  (str zoned-datetime))

(defn duration->human-readable-str [dur]
  (let [millis (.toMillis dur)
        secs (quot millis 1000)
        hours (quot secs 3600)
        minutes (quot (mod secs 3600) 60)
        seconds (mod secs 60)]
    (format "%d:%02d:%02d" hours minutes seconds)))

(defn time-exceeds-interval? [curr-run-time last-run-time]
  (let [fake-last-run-time (java.time.ZonedDateTime/of 2020 5 18 12  0 0 0 chicago-tz)
        last-run-time (or last-run-time fake-last-run-time)

        last-run-delta (java.time.Duration/between last-run-time curr-run-time)
        compare-val (.compareTo last-run-delta run-interval)
        last-run-gt-interval? (> compare-val 0)]
    last-run-gt-interval?))

(defn sh-success? [{:keys [exit] :as _sh-res}]
  (= exit 0))

(defn delete-dir-and-verify! [dir-path]
  (let [delete-res (sh "rm" "-rf" dir-path)
        _ (when-not (sh-success? delete-res)
            (throw (ex-info "delete failed" {:dir-path dir-path
                                             :delete-res delete-res})))
        dir-f (io/file dir-path)
        _ (when (.exists dir-f)
            (throw (ex-info "dir should not exist" {:dir-path dir-path
                                                    :delete-res delete-res})))]))

(defn mkdir-and-verify! [dir-path]
  (let [mkdir-res (sh "mkdir" "-p" dir-path)
        _ (when-not (sh-success? mkdir-res)
            (throw (ex-info "could not create dir" {:dir-path dir-path
                                                    :mkdir-res mkdir-res})))
        dir-f (io/file dir-path)
        _ (when-not (.exists dir-f)
            (throw (ex-info "dir should exist" {:dir-path dir-path
                                                :mkdir-res mkdir-res})))]))

(defn ensure-dir-exists! [dir-path]
  (let [dir-f (io/file dir-path)
        _ (when-not (.exists dir-f)
            (mkdir-and-verify! dir-path))]))

(defn time-str->time-obj [time-str]
  (when time-str
    (java.time.ZonedDateTime/parse time-str)))


(defn backup-to-local! [{:keys [ignore-last-run-time?
                                curr-run-time
                                is-prod?
                                borg-repo-path] :as working-data}]
  (let [last-borg-time-str (get-in working-data [:last-run-data :borg-create :success-run-time])
        last-borg-time (time-str->time-obj last-borg-time-str)]
    (if (and (not ignore-last-run-time?)
             (not (time-exceeds-interval? curr-run-time last-borg-time)))
      (let [_ (println "not doing borg create - last time is within interval")]
        working-data)
      (let [borg-repo-f (io/file borg-repo-path)
            _ (when (and (not is-prod?)
                         (.exists borg-repo-f))
                (let [_ (println "test - deleting repo")
                      _ (delete-dir-and-verify! borg-repo-path)]))
            _ (when-not (.exists borg-repo-f)
                (let [_ (println "repo does not exist, initializing")
                      init-return
                      (sh borg-cmd
                          "init"
                          "--encryption=none"
                          "--make-parent-dirs"
                          borg-repo-path)
                      init-success? (sh-success? init-return)
                      _ (when-not init-success?
                          (throw (ex-info "initialize failed" {:init-return init-return})))

                      ;; borg create --stats --list --verbose --patterns-from ~/Documents/borg-patterns.lst /Volumes/Macintosh HD/borg-backups::2020-05-08_01~

                      borg-create-return
                      (sh borg-cmd
                          "create"
                          "--stats"
                          "--list"
                          "--verbose"
                          "--patterns-from" (str home-dir "/Documents/borg-patterns.lst")
                          (str borg-repo-path "::" (time->file-nameable-str curr-run-time))
                          home-dir)

                      borg-create-log (str "borg create\n"
                                           "------------------------\n"
                                           (fmt-for-console borg-create-return))
                      _ (println "\n\n" borg-create-log)

                      borg-prune-return
                      (sh borg-cmd
                          "prune"
                          "--verbose"
                          "--list"
                          "--keep-daily=7"
                          "--keep-weekly=4"
                          "--keep-monthly=6"
                          borg-repo-path)

                      borg-prune-log (str "borg prune:\n"
                                          "-----------------------------\n"
                                          (fmt-for-console borg-prune-return))

                      _ (println "\n\n" borg-prune-log)

                      _ (ensure-dir-exists! logs-dir)
                      borg-run-fname (str "borg-run-" (time->file-nameable-str curr-run-time))

                      _ (spit (str logs-dir "/" borg-run-fname ".log")
                              (str borg-create-log
                                   "\n\n"
                                   (str borg-prune-log)))

                      working-data (assoc-in
                                    working-data
                                    [:borg-create :success-run-time]
                                    (time->parsable-str curr-run-time))

                      ]
                  working-data))]))))

(def temp-dir-path (str home-dir "/Documents/temp"))
(def dest-mount-tgt-path (str temp-dir-path "/dest-drive"))
(def dest-mount-tgt-f (io/file dest-mount-tgt-path))
(def dest-test-path (str dest-mount-tgt-path "/test"))

(defn remove-the-mount! []
  (let [_ (sh "diskutil"
              "unmount"
              "force"
              dest-mount-tgt-path)

        objs-in-dir (-> dest-mount-tgt-f
                        (.list)
                        vec)
        _ (when-not (empty? objs-in-dir)
            (throw (ex-info "dest tgt should be an empty dir" {})))

        _ (delete-dir-and-verify! dest-mount-tgt-path)]))

(defn add-sync-success-time-to-working-data [{:keys [curr-run-time] :as working-data}]
  (assoc-in
   working-data
   [:rsync :success-time]
   (time->parsable-str curr-run-time)))

(defn sync-to-dest-drive! [{:keys [ignore-last-run-time?
                                   curr-run-time
                                   fake-sync?
                                   is-prod?
                                   borg-repo-path] :as working-data}]
  (let [last-sync-time-str (get-in working-data [:last-run-data :rsync :success-run-time])
        last-sync-time (time-str->time-obj last-sync-time-str)]
    (if (and (not ignore-last-run-time?)
             (not (time-exceeds-interval? curr-run-time last-sync-time)))
      (let [_ (println "not doing sync - last time is within interval")]
        working-data)
      (if (and (not is-prod?)
               fake-sync?)
        (let [_ (println "faking sync")]
          (add-sync-success-time-to-working-data working-data))
        (let [_ (println "running sync")

              _ (when (.exists dest-mount-tgt-f)
                  (let [_ (println "dest mount exists. removing")
                        _ (remove-the-mount!)]))

              _ (println "creating dest mount target dir")
              _ (mkdir-and-verify! dest-mount-tgt-path)

              _ (println "mounting target drive")
              mount-res (sh "mount" "-t" "smbfs" "<drive path>" dest-mount-tgt-path)
              _ (when-not (sh-success? mount-res)
                  (throw (ex-info "mount failed" {:mount-res mount-res})))

              dest-drive-path (if is-prod? dest-mount-tgt-path dest-mount-test-path)
              _ (when-not is-prod?
                  (ensure-dir-exists! dest-drive-path))

              new-bkp-path (str dest-drive-path "/borg-backups")
              new-bkp-f (io/file new-bkp-path)

              _ (when (.exists new-bkp-f)
                  (let [old-bkp-path (str dest-drive-path "/borg-backups-old")
                        old-bkp-f (io/file old-bkp-path)
                        _ (when (.exists old-bkp-path)
                            (let [_ (println "old backup exists, deleting")
                                  _ (delete-dir-and-verify! old-bkp-path)]))

                        _ (sh "mv" new-bkp-path old-bkp-path)
                        _ (when (.exists new-bkp-f)
                            (throw (ex-info "should not exist" {:new-bkp-path new-bkp-path})))
                        _ (when-not (.exists old-bkp-f)
                            (throw (ex-info "should exist" {:old-bkp-path old-bkp-path})))]))

              _ (println "doing the sync")
              sync-res (sh "rsync"
                           "-vzrPh"
                           borg-repo-path
                           dest-drive-path)
              _ (when-not (sh-success? sync-res)
                  (throw (ex-info "sync failed" {:sync-res sync-res})))
              dest-borg-repo-path (str dest-drive-path "/" borg-repo-dir-name)
              dest-borg-repo-f (io/file dest-borg-repo-path)
              _ (when-not (.exists dest-borg-repo-f)
                  (throw (ex-info "dest borg repo should exist" {:dest-borg-repo-path dest-borg-repo-path
                                                                 :sync-res sync-res})))

              _ (println "removing the mount and dir")
              _ (remove-the-mount!)

              working-data (add-sync-success-time-to-working-data working-data)]
          working-data)))))


(def last-run-data-path (str borg-working-dir "/last-run.edn"))
(def last-run-data-f (io/file last-run-data-path))

(defn main! [{:keys [curr-run-time is-prod?] :as working-data}]
  (let [_ (println (str "executing borg. time = " (time->file-nameable-str curr-run-time)))

        full-last-run-data (when (.exists last-run-data-f)
                             (edn/read-string (slurp last-run-data-path)))
        _ (pp/pprint {:full-last-run-data full-last-run-data})

        last-run-data (when full-last-run-data
                        (if is-prod?
                          (:prod full-last-run-data)
                          (:test full-last-run-data)))

        $ (assoc working-data :last-run-data last-run-data)
        borg-repo-path (if is-prod?
                         borg-repo-local-prod-path
                         borg-repo-local-test-path)
        $ (assoc $ :borg-repo-path borg-repo-path)
        $ (backup-to-local! $)

        $ (sync-to-dest-drive! $)

        last-run-data-new (select-keys $ [:rsync :borg-create])
        full-last-run-data-new (assoc full-last-run-data (if is-prod? :prod :test) last-run-data-new)

        _ (spit last-run-data-path
                (with-out-str
                  (pp/pprint full-last-run-data-new)))

        ]))


(def cli-options
  [["-p" "--production" "run as production"
    :default false]
   ["-i" "--ignore-last-run-time" "ignore last run time"
    :default false]
   ["-f" "--fake-sync" "fake sync - only works when not prod"
    :default false]])


(try
  (let [{:keys [options _arguments _errors _summary]} (cli/parse-opts *command-line-args* cli-options)
        _ (pp/pprint {:options options})
        {:keys [production ignore-last-run-time fake-sync]} options]
    (if (.exists lock-f)
      (println "cannot run... lock file exists")
      (let [now (java.time.ZonedDateTime/now)
            curr-run-time (.withZoneSameInstant now chicago-tz)
            _ (spit lock-f (time->parsable-str curr-run-time))
            _ (when-not (.exists lock-f)
                (throw (ex-info "lock file should exist" {})))
            working-data (merge options
                                {:curr-run-time curr-run-time
                                 :ignore-last-run-time? ignore-last-run-time
                                 :fake-sync? fake-sync
                                 :is-prod? production})]
        (main! working-data)
        (.delete lock-f))))
  (catch Throwable throwable
    (stacktrace/print-stack-trace throwable)
    (sh "osascript" "-e" "'display dialog \"Backup failure - check logs\"'")
    (.delete lock-f)))

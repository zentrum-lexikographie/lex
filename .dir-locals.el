;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")

((python-mode
  (python-test-runner pytest))
 (clojure-mode
  (cider-preferred-build-tool . "clojure-cli")
  (cider-clojure-cli-aliases . ":test:log:client:server:oxygen")
  (cider-ns-refresh-before-fn . "integrant.repl/halt")
  (cider-ns-refresh-after-fn . "integrant.repl/go")))

;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")

((python-mode
  (python-test-runner pytest))
 (clojure-mode
  . ((cider-clojure-cli-global-options . "-A:test:log:client:server:oxygen"))))

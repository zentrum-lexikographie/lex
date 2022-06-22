(ns zdl.lex.editor-test
  (:require  [clojure.test :refer [deftest is use-fixtures join-fixtures]]
             [zdl.lex.fixture.git :as fixture.git]
             [zdl.lex.fixture.system :as fixture.system]
             [zdl.lex.server.article.editor :as server.article.editor]))

(use-fixtures :once
  (join-fixtures [fixture.git/newer-articles fixture.system/instance]))

(deftest edits
  (let [git-dir fixture.system/git-dir
        lock-db (get fixture.system/*system* :zdl.lex.server.article.lock/db)]
    (server.article.editor/edit! git-dir lock-db)
    (is true)))

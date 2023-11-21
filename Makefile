test:
	clojure -M:dev -m kaocha.runner

autotest:
	clojure -M:dev -m kaocha.runner --watch

.PHONY: test

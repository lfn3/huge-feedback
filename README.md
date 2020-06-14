# huge-feedback

At the moment I haven't tried running this outside of my development environment.
First step is to configure the application. I've used something like:
```
{:huge-feedback.apis.gitlab/config   {:huge-feedback.apis.gitlab/base-url   "https://gitlab.com/api/v4"
                                      :huge-feedback.apis.gitlab/project-id 13083
                                      :huge-feedback.apis.gitlab/token      "REDACTED"}
 :huge-feedback.config/use-cors-proxy? true}
```
in `src/main/resources/config.edn`, which the frontend will request and pick up as a default.
(Or you can paste something into the `config` text box)

Once you've attached a repl (`lein repl` or similar) invoke `(mount/start)` in 
`huge-feedback.core` ns to start the webserver and js build loop.
You can then attach a second nrepl to `7888` (the figwheel port) which will 
allow you to get into the js process by calling `(figwheel.main.api/cljs-repl fig-build-id)`,
again from `huge-feedback.core`. Finally to acutally get some data into the app, 
invoke `(poll-gitlab-once)` from `core.cljs`. 
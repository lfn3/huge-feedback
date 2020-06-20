load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "3.2"
RULES_JVM_EXTERNAL_SHA = "82262ff4223c5fda6fb7ff8bd63db8131b51b413d26eb49e3131037e79e324af"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")

maven_install(
    artifacts = [
        maven.artifact(
            "org.clojure",
            "clojure",
            "1.10.1",
            exclusions = [
                "org.clojure:core.specs.alpha",
                "org.clojure:spec.alpha",
            ]
        ),
        maven.artifact(
            "org.clojure",
            "spec.alpha",
            "0.2.187",
            exclusions = [
                "org.clojure:clojure"
            ]
        ),
        maven.artifact(
            "org.clojure",
            "core.specs.alpha",
            "0.2.44",
            exclusions = [
                "org.clojure:clojure"
            ]
        ),
        # clj deps
        "cheshire:cheshire:5.8.1",
        "ring:ring-core:1.7.1",
        "ring:ring-jetty-adapter:1.7.1",
        "mount:mount:0.1.16",
        "bidi:bidi:2.1.5",
        "cljs-ajax:cljs-ajax:0.8.0",

        # cljs deps
        "org.clojure:clojurescript:1.10.339",
        "re-frame:re-frame:0.10.6",
        "nrepl:nrepl:0.6.0",
        "com.bhauman:figwheel-main:0.1.9",
        "cider:piggieback:0.4.0",
        "day8.re-frame:re-frame-10x:0.3.3",
        "org.eclipse.jetty.websocket:websocket-server:9.4.12.v20180830",
        "org.eclipse.jetty.websocket:websocket-servlet:9.4.12.v20180830"
    ],
    repositories = [
        "https://clojars.org/repo/",
        "https://jcenter.bintray.com/",
        "https://maven.google.com",
        "https://repo1.maven.org/maven2",
    ],
)

RULES_CLOJURE_TAG = "a4a6faba288d68273c97cc1c9c8b574937503ce8"
RULES_CLOJURE_SHA = "6ec70e6b7937b65f7719b097a1115c8f9e584d56f7e6f9d1ec00fd6a5ad155fe"

http_archive(
    name = "rules_clojure",
    strip_prefix = "rules_clojure-%s" % RULES_CLOJURE_TAG,
    sha256 = RULES_CLOJURE_SHA,
    url = "https://github.com/simuons/rules_clojure/archive/%s.zip" % RULES_CLOJURE_TAG,
)
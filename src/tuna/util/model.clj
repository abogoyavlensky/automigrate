(ns tuna.util.model)


(defn kw->vec
  [kw]
  (when (qualified-keyword? kw)
    (mapv keyword
      ((juxt namespace name) kw))))

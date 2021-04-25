(ns tuna.util.map)


(defn dissoc-in
  "Dissociate keys from nested map."
  [m ks ks-to-dissoc]
  ; TODO: receive `ks-to-dissoc` as `& args`
  (apply (partial update-in m ks dissoc) ks-to-dissoc))

(ns automigrate.util.map)


(defn dissoc-in
  "Dissociate keys from nested map."
  [m ks & ks-to-dissoc]
  (apply (partial update-in m ks dissoc) ks-to-dissoc))

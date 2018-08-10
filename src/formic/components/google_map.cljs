(ns formic.components.google-map
  (:require
   [reagent.core :as r]
   [formic.util :as fu]
   [formic.field :as field]
   [clojure.string :as s]
   ))

(defn map->lat-lng [{:keys [lat lng]}]
  (when (and lat lng)
    (js/google.maps.LatLng. lat lng)))

(defn geocode-position [geocoder value]
  (.geocode @geocoder
            (clj->js {:location (map->lat-lng @value)})
            (fn [results status]
              (when (= status "OK")
                (swap! value assoc
                       :address (.-formatted_address (first results)))))))

(defn google-map [{:keys [value touched err options] :as f}]
  (let [{:keys [autocomplete default-lat-lng]} options
        state (r/atom :init)
        map (r/atom nil)
        map-holder-el (r/atom nil)
        autocomplete-input-el (r/atom nil)
        autocompleter (r/atom nil)
        address-val (r/atom "")
        marker (r/atom nil)
        geocoder (r/atom nil)
        default-lat-lng (or default-lat-lng {:lat 35.6895 :lng 139.6917})
        update-on-event (fn [ev]
                          (reset! touched true)
                          (.stop ev)
                          (swap! value
                                 assoc
                                 :lat (ev.latLng.lat)
                                 :lng (ev.latLng.lng))
                          (when autocomplete (geocode-position geocoder value)))]
    (r/create-class
     {:component-will-mount
      (fn [_ _]
        (when (and js/navigator.geolocation
                   (and
                    (nil? (:lat @value))
                    (nil? (:lng @value))))
          (js/navigator.geolocation.getCurrentPosition
           (fn [pos]
             (swap! value assoc
                    :lat (.. pos -coords -latitude)
                    :lng (.. pos -coords -longitude))
             (when autocomplete (geocode-position geocoder value)))))
        (r/track! (fn []
                    (when (and @map
                               @marker
                               (:lat @value)
                               (:lng @value))
                      (let [latlng (js/google.maps.LatLng.
                                    (:lat @value)
                                    (:lng @value))]
                        (.panTo @map latlng)
                        (.setPosition @marker latlng))))))
      :component-did-mount
      (fn [this _]
        ;; create map
        (reset! map
                (js/google.maps.Map.
                 @map-holder-el
                 (clj->js {:center (js/google.maps.LatLng. (:lat default-lat-lng) (:lng default-lat-lng))
                           :zoom 12})))
        ;; create marker
        (reset! marker
                (js/google.maps.Marker.
                 (clj->js {:center (js/google.maps.LatLng.
                                    (:lat default-lat-lng) (:lng default-lat-lng))
                           :map @map
                           :draggable true
                           :animation js/google.maps.Animation.DROP
                           })))
        ;; update on marker drag
        (js/google.maps.event.addListener @marker "dragend" update-on-event)
        ;; reset marker position on map click
        (js/google.maps.event.addListener @map "click" update-on-event)
        ;; create autocomplete
        (when autocomplete
          (reset! geocoder (google.maps.Geocoder.))
          (reset! autocompleter (google.maps.places.Autocomplete. @autocomplete-input-el (clj->js {:types ["geocode" "establishment"]})))
          (.bindTo @autocompleter "bounds" @map)
          ;; update on autocomplete
          (.addListener @autocompleter
                        "place_changed"
                        (fn []
                          (reset! touched true)
                          (if-let [place (.getPlace @autocompleter)]
                            (when place.geometry
                              (if place.geometry.viewport
                                (.fitBounds @map place.geometry.viewport)
                                (do
                                  (.setCenter @map place.geometry.location)
                                  (.setZoom @map 17)))
                              (swap! value assoc
                                     :address (.. @autocomplete-input-el -value)
                                     :lat (place.geometry.location.lat)
                                     :lng (place.geometry.location.lng)))
                            (swap! value assoc :address :not-found)))))
        (reset! state :active))
      :reagent-render
      (fn [{:keys [value err] :as f}]
        [:div
         [:h5.formic-input-title (fu/format-kw (:id f))]
         [:div.formic-google-map
          [:div.formic-map-wrapper
           (when autocomplete
             [:label.formic-auto-complete
              [:span.formic-input-tutle "Address:"]
              [:input
               {:value (case (:address @value)
                         :not-found ""
                         nil ""
                         (:address @value))
                :on-change #(swap! value assoc :address (.. % -target -value))
                :ref (fn [el] (reset! autocomplete-input-el el))}]
              (when (= :not-found (:address @value))
                [:h4.not-found "Not Found."])])
           [:div.formic-map-holder
            {:ref (fn [el] (reset! map-holder-el el))}]
           [:label.formic-lat-lng
            [:span "Lat:"]
            [:input {:type 'number
                     :step 0.0001
                     :min 0
                     :on-change (fn [ev]
                                  (reset! touched true)
                                  (when-let [v (not-empty (.. ev -target -value))]
                                    (swap! value assoc :lat (js/parseFloat v))
                                    (when autocomplete (geocode-position geocoder value))))
                     :value (if-let [lat (:lat @value)]
                              (.toPrecision lat 8)
                              "")}]]
           [:label.formic-lat-lng
            [:span "Long:"]
            [:input {:type 'number
                     :step 0.0001
                     :min 0
                     :on-change (fn [ev]
                                  (reset! touched true)
                                  (when-let [v (not-empty (.. ev -target -value))]
                                    (swap! value assoc :lng (js/parseFloat v))
                                    (when autocomplete (geocode-position geocoder value))))
                     :value (if-let [lat (:lng @value)]
                              (.toPrecision lat 8)
                              "")}]]]]])})))
(field/register-component
 :formic-google-map
 {:component google-map})

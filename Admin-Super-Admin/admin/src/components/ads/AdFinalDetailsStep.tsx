import * as React from "react"
import { useFormContext } from "react-hook-form"
import { CTASelector, type CTAType } from "./CTASelector"
import { CustomSectionsBuilder } from "./CustomSectionsBuilder"
import { Tag, Link as LinkIcon, Map as MapIcon, Info, Loader2 } from "lucide-react"
import { GoogleMap, useJsApiLoader, Marker } from "@react-google-maps/api"

const GOOGLE_MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string

const mapContainerStyle = {
  width: "100%",
  height: "100%",
}

// ── Parse "lat, lng" string into { lat, lng } or null ──────────────────────
function parseCoords(raw: string): { lat: number; lng: number } | null {
  if (!raw?.trim()) return null
  const parts = raw.split(",").map((s) => s.trim())
  if (parts.length !== 2) return null
  const lat = parseFloat(parts[0])
  const lng = parseFloat(parts[1])
  if (isNaN(lat) || isNaN(lng)) return null
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
  return { lat, lng }
}

// ── Google Maps interactive preview ───────────────────────────────────────
function MapPreview({ 
  coordinatesRaw, 
  onMapClick 
}: { 
  coordinatesRaw: string
  onMapClick: (lat: number, lng: number) => void
}) {
  const coords = parseCoords(coordinatesRaw)
  
  const { isLoaded } = useJsApiLoader({
    id: "google-map-script",
    googleMapsApiKey: GOOGLE_MAPS_API_KEY,
  })

  const center = coords || { lat: 19.0760, lng: 72.8777 } // default Mumbai

  if (!isLoaded) {
    return (
      <div className="relative border border-gray-200 dark:border-gray-800 rounded-2xl overflow-hidden bg-gray-100 dark:bg-[#1C1F26] aspect-video flex items-center justify-center">
        <Loader2 className="w-8 h-8 text-brand-500 animate-spin" />
      </div>
    )
  }

  return (
    <div className="relative border border-gray-200 dark:border-gray-800 rounded-2xl overflow-hidden aspect-video shadow-sm">
      <GoogleMap
        mapContainerStyle={mapContainerStyle}
        center={center}
        zoom={coords ? 15 : 5}
        onClick={(e) => {
          if (e.latLng) {
            onMapClick(e.latLng.lat(), e.latLng.lng())
          }
        }}
        options={{
          disableDefaultUI: false,
          zoomControl: true,
          streetViewControl: false,
          mapTypeControl: false,
        }}
      >
        {coords && <Marker position={coords} />}
      </GoogleMap>

      {!coords && (
        <div className="absolute inset-0 pointer-events-none flex items-center justify-center z-10">
          <div className="bg-white/90 dark:bg-[#1A1D24]/90 backdrop-blur-sm px-5 py-3 rounded-xl shadow-lg border border-gray-200 dark:border-gray-800 text-center">
            <MapIcon className="w-6 h-6 text-brand-500 mx-auto mb-1.5" />
            <p className="text-sm font-bold text-gray-900 dark:text-white">Select Location</p>
            <p className="text-xs text-gray-500 mt-0.5">Click anywhere on the map to drop a pin</p>
          </div>
        </div>
      )}

      {/* Coords badge */}
      {coords && (
        <div className="absolute bottom-6 left-3 bg-white/90 dark:bg-[#1A1D24]/90 backdrop-blur-sm px-3 py-1.5 rounded-lg border border-gray-200 dark:border-gray-700 flex items-center gap-2 shadow-sm pointer-events-none z-10">
          <div className="w-2 h-2 rounded-full bg-brand-500 animate-pulse" />
          <span className="text-[11px] font-semibold text-gray-600 dark:text-gray-300">
            {coords.lat.toFixed(5)}, {coords.lng.toFixed(5)}
          </span>
        </div>
      )}
    </div>
  )
}

// ── Main component ──────────────────────────────────────────────────────────
export function AdFinalDetailsStep() {
  const { register, watch, setValue, formState: { errors } } = useFormContext()

  const ctaType = watch("ctaType") as CTAType
  const ctaActionValue = watch("ctaActionValue") as string

  const getActionLabel = () => {
    switch (ctaType) {
      case "Redirect": return "Website URL"
      case "Dial": return "Phone Number"
      case "WhatsApp": return "WhatsApp Number"
      case "Email": return "Email Address"
      case "Map": return "Map Location Coordinates (lat, lng)"
      default: return "Action Value"
    }
  }

  const getPlaceholder = () => {
    switch (ctaType) {
      case "Redirect": return "https://example.com"
      case "Dial":
      case "WhatsApp": return "e.g. 9876543210"
      case "Email": return "e.g. contact@business.com"
      case "Map": return "e.g. 19.0760, 72.8777"
      default: return ""
    }
  }

  return (
    <div className="space-y-10">

      {/* ── Section 0: Header ── */}
      <div className="space-y-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900 dark:text-white">Ad Details</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Configure the Call-to-Action for this advertisement
          </p>
        </div>
      </div>

      <div className="h-px bg-gray-100 dark:bg-gray-800 w-full" />

      {/* ── Section 1: CTA Configuration ── */}
      <div className="space-y-6">
        <div>
          <h2 className="text-xl font-bold text-gray-900 dark:text-white">Call to Action (CTA)</h2>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            Configure the main action button for your advertisement
          </p>
        </div>

        <CTASelector
          value={ctaType}
          onChange={(val) => setValue("ctaType", val, { shouldValidate: true })}
        />

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 p-6 bg-gray-50 dark:bg-[#1C1F26] border border-gray-200 dark:border-gray-800 rounded-2xl">
          {/* Button Label */}
          <div>
            <label className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <Tag className="w-4 h-4 text-brand-500" />
              Button Label
            </label>
            <input
              {...register("ctaLabel")}
              placeholder="e.g. Shop Now, Contact Us..."
              className={`w-full px-4 py-3 rounded-xl border bg-white dark:bg-[#1A1D24] text-gray-900 dark:text-white focus:outline-none focus:ring-2 transition-all ${errors.ctaLabel
                ? "border-red-300 focus:border-red-500 focus:ring-red-500/20"
                : "border-gray-200 dark:border-gray-700 focus:border-brand-500 focus:ring-brand-500/20"
                }`}
            />
            {errors.ctaLabel && (
              <p className="mt-1.5 text-xs text-red-500 font-medium">{errors.ctaLabel.message as string}</p>
            )}
          </div>

          {/* Action Value */}
          <div>
            <label className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300 mb-2">
              <LinkIcon className="w-4 h-4 text-brand-500" />
              {getActionLabel()}
            </label>
            <input
              {...register("ctaActionValue")}
              placeholder={getPlaceholder()}
              className={`w-full px-4 py-3 rounded-xl border bg-white dark:bg-[#1A1D24] text-gray-900 dark:text-white focus:outline-none focus:ring-2 transition-all ${errors.ctaActionValue
                ? "border-red-300 focus:border-red-500 focus:ring-red-500/20"
                : "border-gray-200 dark:border-gray-700 focus:border-brand-500 focus:ring-brand-500/20"
                }`}
            />
            {errors.ctaActionValue && (
              <p className="mt-1.5 text-xs text-red-500 font-medium">{errors.ctaActionValue.message as string}</p>
            )}
          </div>
        </div>

        {/* ── Live Google Map preview when CTA = Map ── */}
        {ctaType === "Map" && (
          <MapPreview 
            coordinatesRaw={ctaActionValue} 
            onMapClick={(lat, lng) => {
              setValue("ctaActionValue", `${lat.toFixed(5)}, ${lng.toFixed(5)}`, { shouldValidate: true })
            }}
          />
        )}
      </div>

      <div className="h-px bg-gray-100 dark:bg-gray-800 w-full" />

      {/* ── Section 2: Dynamic Content Sections ── */}
      <CustomSectionsBuilder />

      {/* Helper Alert */}
      <div className="flex items-start gap-4 p-5 bg-blue-50 dark:bg-blue-500/5 border border-blue-100 dark:border-blue-900/30 rounded-2xl">
        <Info className="w-5 h-5 text-blue-500 flex-shrink-0 mt-0.5" />
        <div className="space-y-1">
          <p className="text-sm font-bold text-blue-900 dark:text-blue-100 leading-tight">Advanced Customization</p>
          <p className="text-xs text-blue-700/70 dark:text-blue-400/70 leading-relaxed">
            Unlike standard ads, Keliri allows you to add multiple descriptive blocks.
            Use these to highlight key product features or unique selling points.
          </p>
        </div>
      </div>
    </div>
  )
}
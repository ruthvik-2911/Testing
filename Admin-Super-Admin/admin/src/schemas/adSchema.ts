import { z } from "zod"

export const adSchema = z.object({
  title: z.string().min(3, "Title must be at least 3 characters").max(100, "Title is too long"),
  description: z.string().min(10, "Description must be at least 10 characters").max(500, "Description is too long"),
  type: z.enum(["Banner", "Video", "Image Ad"]),
  companyUID: z.string().optional(),
  mediaFile: z.any().optional(),
  mediaUrl: z.string().optional(),

  ctaType: z.enum(["Redirect", "Dial", "WhatsApp", "Email", "Map"]),
  ctaLabel: z.string().min(1, "Button label is required"),
  ctaActionValue: z.string().min(1, "Action value is required"),
  customSections: z.array(z.object({
    title: z.string().min(1, "Section title is required"),
    description: z.string().min(1)
  })).min(1, "At least one section is required"),

  locationMode: z.enum(["manual", "auto", "preset"]),
  latitude: z.number({ error: "Latitude must be a valid number" }).min(-90).max(90),
  longitude: z.number({ error: "Longitude must be a valid number" }).min(-180).max(180),
  radius: z.number({ error: "Radius must be a valid number" }).min(0.1, "Radius must be at least 0.1 KM").max(500, "Maximum radius is 500 KM"),
})

export type AdFormData = z.infer<typeof adSchema>
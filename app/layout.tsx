import type { Metadata, Viewport } from "next";
import { headers } from "next/headers";
import { OfflineStatus, PwaProvider } from "./components/pwa";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const requestHeaders = await headers();
  const host = requestHeaders.get("x-forwarded-host") ?? requestHeaders.get("host") ?? "localhost:3000";
  const protocol = requestHeaders.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");
  const origin = `${protocol}://${host}`;

  return {
    title: "Copy Challenge",
    description: "Mémoriser quelques mots, les écrire, puis avancer à son rythme.",
    manifest: "/manifest.webmanifest",
    applicationName: "Copy Challenge",
    appleWebApp: { capable: true, statusBarStyle: "default", title: "Copy Challenge" },
    icons: { icon: "/favicon.svg", shortcut: "/favicon.svg" },
    openGraph: {
      title: "Copy Challenge — Challenge de mémoire",
      description: "Un challenge visuel qui masque les mots quand l’élève regarde son cahier.",
      images: [{ url: `${origin}/og.png`, width: 1200, height: 630, alt: "Copy Challenge, challenge de mémoire" }],
      locale: "fr_FR",
      type: "website",
    },
  };
}

export const viewport: Viewport = {
  themeColor: "#f5efe5",
  colorScheme: "light",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="fr">
      <body>
        {children}
        <PwaProvider />
        <OfflineStatus />
      </body>
    </html>
  );
}

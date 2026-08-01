import { DictaApp } from "./DictaApp";

export const metadata = {
  title: "Copy Challenge — mémoriser, écrire, progresser",
  description: "Une dictée visuelle qui masque les mots quand l’élève regarde son cahier.",
};

export default function Home() {
  return <DictaApp />;
}

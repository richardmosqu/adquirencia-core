/* ============================================================
   Landing — Operations & Acquiring Core
   Dos comportamientos, sin dependencias:
   1) las tarjetas aparecen al entrar en pantalla;
   2) la CTA fija de móvil se muestra al dejar atrás el hero.

   Se resuelve midiendo posiciones en cada scroll (limitado con
   requestAnimationFrame) en vez de con IntersectionObserver: es
   igual de suave y no depende de cómo cada navegador calcule la
   raíz del observador. Si el usuario pidió menos movimiento, todo
   queda visible y quieto desde el inicio.
   ============================================================ */

(() => {
  "use strict";

  const items = [...document.querySelectorAll(".reveal")];
  const cta = document.querySelector(".sticky-cta");
  const hero = document.querySelector(".hero");
  const endCta = document.querySelector("#vision .btn");
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (reduced) {
    items.forEach((el) => el.classList.add("in"));
  } else {
    // las tarjetas de un mismo grupo entran en cascada
    items.forEach((el) => {
      const group = [...el.parentElement.children].filter((s) => s.classList.contains("reveal"));
      el.style.transitionDelay = `${Math.min(group.indexOf(el), 5) * 60}ms`;
    });
  }

  let pending = [];

  function update() {
    const h = window.innerHeight;
    if (!reduced) {
      // se muestra en cuanto el borde superior entra en el 88% de la pantalla
      pending = pending.filter((el) => {
        if (el.getBoundingClientRect().top > h * 0.88) return true;
        el.classList.add("in");
        return false;
      });
    }
    if (cta && hero) {
      // visible tras el hero, y se retira al llegar a la CTA del cierre
      // para no mostrar dos botones iguales encimados
      const pasoElHero = hero.getBoundingClientRect().bottom <= 0;
      const yaSeVeElFinal = endCta && endCta.getBoundingClientRect().top < h - 40;
      cta.classList.toggle("show", pasoElHero && !yaSeVeElFinal);
    }
  }

  // update() se llama directo en cada scroll: la lista pendiente se vacía
  // enseguida y a partir de ahí solo mide un rectángulo (el del hero).
  pending = reduced ? [] : items.slice();
  update();
  window.addEventListener("scroll", update, { passive: true });
  window.addEventListener("resize", update);
  window.addEventListener("load", update);
})();

import Vue from "vue";
import VueRouter from "vue-router";
import SearchForm from "../views/SearchForm.vue";

Vue.use(VueRouter);

const routes = [
  {
    path: "",
    name: "home",
    component: SearchForm
  },
  {
    path: "/",
    redirect: { name: "home" }
  },
  {
    path: "/adresni-mista-v-okoli",
    name: "nearbyAddresses",
    component: () => import("../views/NearbyAddresses.vue")
  },
  {
    path: "/adresni-mista/:id(\\d+)",
    name: "addressDetail",
    component: () => import("../views/AddressDetail.vue")
  },
  {
    path: "*",
    component: () => import("../components/NotFound.vue")
  }
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes
});

export default router;

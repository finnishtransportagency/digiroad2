var http = require('http');
var util = require('util');





var dataOutide ="{\n" +
    "  \"muutos_tieto\": [\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5994,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 60,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5995,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 410,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 327,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5996,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 410,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 327,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 410,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 327,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5997,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 60,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 60,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5998,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 327,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 60,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 327,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 60,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5999,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 750,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 410,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 6241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 750,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 410,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8878,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 3781,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 2933,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8879,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 529,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 440,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 529,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 440,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8880,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 425,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 89,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 425,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 89,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8881,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 425,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 425,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8882,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2581,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 556,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2581,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 556,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8883,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 556,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 529,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 556,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 529,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8884,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 425,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 89,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 425,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 89,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8885,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2933,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2761,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2933,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2761,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8886,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2761,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2581,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2761,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2581,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8887,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2761,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2581,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2761,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2581,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8888,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 529,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 440,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 529,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 440,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8889,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 425,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 425,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8890,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 89,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 89,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8891,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2933,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2761,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2933,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2761,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-05-05T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8892,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 3781,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2933,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 14,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 3781,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2933,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6946,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5193,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5157,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6947,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3149,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3135,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6948,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3191,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6949,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5147,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5123,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6950,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5157,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5147,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6951,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3149,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6952,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3135,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 3084,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 6953,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3084,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 108,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3084,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 108,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 6954,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 6955,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6956,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3135,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 3084,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3135,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 3084,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6957,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3149,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3149,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6958,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5123,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3191,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5123,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3191,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6959,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5157,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5147,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5157,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5147,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6960,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5785,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5193,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5785,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5193,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6961,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3149,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3135,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3149,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3135,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6962,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5944,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5785,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5944,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5785,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6963,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5193,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5157,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5193,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5157,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6964,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3191,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3191,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-08T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6965,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5147,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5123,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 180,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5147,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5123,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 2471,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5732,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 5388,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2472,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3133,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2939,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3133,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2939,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2473,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3307,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3307,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2474,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3307,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3307,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2475,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3133,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2939,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 3133,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2939,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2476,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 2711,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1155,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 2711,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1155,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2477,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 2939,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2711,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 2939,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2711,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2478,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1155,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 628,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1155,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 628,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2479,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 2711,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1155,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 2711,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1155,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2480,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 628,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 628,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 2481,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5388,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3307,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47954,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5388,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3307,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 10492,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5978,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5950,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 10493,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5992,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5978,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 10494,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 6041,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5992,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10495,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4979,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4944,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4979,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4944,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10496,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5009,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 4979,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5009,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 4979,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10497,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4944,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4358,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4944,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4358,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10498,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3433,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3113,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3433,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3113,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10499,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5637,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5607,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5637,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5607,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10500,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5637,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5607,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5637,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5607,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10501,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2253,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1872,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2253,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1872,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10502,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5592,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5009,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5592,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5009,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10503,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1872,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1345,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1872,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1345,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10504,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5682,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5637,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5682,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5637,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10505,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5009,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 4979,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5009,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 4979,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10506,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4358,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4046,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4358,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4046,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10507,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1345,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1244,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1345,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1244,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10508,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 398,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 398,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10509,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3433,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3113,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3433,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3113,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10510,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5607,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5592,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5607,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5592,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10511,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4358,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4046,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4358,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4046,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10512,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 852,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 398,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 852,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 398,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10513,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1137,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 852,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1137,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 852,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10514,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2484,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2253,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2484,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2253,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10515,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1137,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 852,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1137,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 852,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10516,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1137,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1137,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10517,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5950,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5682,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5950,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5682,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10518,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4046,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3433,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4046,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3433,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10519,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2484,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2253,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2484,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2253,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10520,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5607,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5592,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5607,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5592,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10521,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1345,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1244,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1345,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1244,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10522,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4979,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4944,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4979,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4944,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10523,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3113,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2484,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3113,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2484,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10524,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1137,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1137,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10525,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 398,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 398,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10526,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1872,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1345,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1872,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1345,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 10527,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5682,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5637,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5682,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5637,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10528,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 7093,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6814,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 7084,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6805,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10529,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 8147,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8063,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 8138,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8054,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10530,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 7864,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7093,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 7855,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7084,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10531,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5992,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5978,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5992,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5978,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10532,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 8063,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7864,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 8054,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7855,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10533,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 6814,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6041,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 6805,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6034,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10534,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 7093,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6814,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 7084,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6805,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10535,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 6041,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5992,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 6034,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5992,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10536,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5978,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5950,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 5978,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5950,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 10537,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 8147,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8063,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 847,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 8138,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8054,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24268,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3185,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2990,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4445,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24269,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2990,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2710,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4725,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 4445,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24270,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1279,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1080,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6355,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 6156,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24271,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2700,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2290,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5145,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4735,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24272,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2710,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2700,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4735,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4725,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24273,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2710,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2700,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4735,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4725,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24274,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1535,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1279,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6156,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5900,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24275,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2290,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1535,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5900,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5145,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24276,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1535,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1279,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6156,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 5900,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24277,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 7435,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3185,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4250,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24278,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2990,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2710,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4725,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4445,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24279,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1279,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1080,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6355,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6156,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24280,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 884,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 7435,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 6551,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24281,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3185,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2990,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4445,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24282,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1080,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1030,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6355,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24283,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2700,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2290,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5145,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 4735,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-09T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24284,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1030,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 884,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40923,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6551,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-15T00:00:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 52137,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 220,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 3,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48756,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 144,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 111,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 393,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 121,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 197,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 197,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 122,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 196,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 393,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 197,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 122,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 196,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 393,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 197,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 417,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 196,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 418,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 341,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 144,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 197,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 419,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 144,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48756,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 144,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-25T10:03:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 419,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 144,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48756,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 144,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1687,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 150,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1688,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 450,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1689,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 186,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15832,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 150,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15833,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 186,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15834,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 450,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:23:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1699,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40903,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 569,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:23:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1700,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40903,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 185,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:23:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1701,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40903,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 207,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:23:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15844,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40903,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 569,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:23:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15845,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40903,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 114,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:23:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15846,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40903,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 81,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1741,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1742,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1743,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1744,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 334,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1745,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15750,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 334,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15751,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15752,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15753,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15754,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 44241,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 475,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65392,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 142,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 70,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65393,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 457,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 420,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65394,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 63,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65395,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 247,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65396,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 420,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 63,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65398,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 399,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 142,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65399,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 399,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 142,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65400,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 63,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65401,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 142,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 70,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65402,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 70,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65404,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 457,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 420,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65523,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 457,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 420,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65524,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 20,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65525,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 142,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 70,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65526,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 457,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 420,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65527,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 399,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 142,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65528,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 63,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65529,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 142,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 70,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65530,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 247,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 20,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65532,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 63,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65533,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 399,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 142,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65535,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 420,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 63,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:29:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65536,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 70,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1781,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 118,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1782,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 121,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1783,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 118,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1784,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 295,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1785,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 121,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15801,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 195,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15802,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 223,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15803,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 223,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15804,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 195,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15805,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65397,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 122,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 65403,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 122,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65531,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 122,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:43:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 65534,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40901,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 122,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-23T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:57:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1832,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 121,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:57:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1833,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48900,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 295,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:57:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1834,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48900,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 114,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:57:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1835,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 121,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T15:57:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1836,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48900,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 114,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1883,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 152,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1884,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 92,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1885,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 319,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1886,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 92,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1887,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 152,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15882,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 92,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15883,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 152,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15884,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 319,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15885,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 92,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:05:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15886,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40904,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 152,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1928,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1932,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15919,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-06-30T16:12:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15920,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-01T13:26:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 1930,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 620,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-30T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-01T13:26:00+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 2148,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 105,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-01T13:26:00+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 2149,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 515,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 620,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 105,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-01T13:26:00+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15922,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40905,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 620,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-06-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 3606,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 3608,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 3610,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 533,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 3657,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 109,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 3659,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 3660,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-03T11:34:29+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 3661,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 424,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48983,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 533,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 109,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T13:59:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4441,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 6996,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6446,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 6996,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6446,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T13:59:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4442,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 6446,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5885,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 6446,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5885,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T13:59:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4443,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5400,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 331,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5400,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 331,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T13:59:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4444,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 331,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 331,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T13:59:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4445,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5885,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5400,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 5885,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5400,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T13:59:49+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 4446,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 753,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 7749,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 6996,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T15:22:49+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 4446,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 753,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 7749,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 6996,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T15:22:49+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4453,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 305,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 5,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T15:22:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4454,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 753,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 676,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 753,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 4,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T15:58:49+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 4464,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 17065,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 576,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 550,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T15:58:49+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 4465,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 17065,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 761,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 576,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-06T15:58:49+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4466,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 17065,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 550,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 17065,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 550,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-06T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T13:37:35+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4482,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8162,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T13:37:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4483,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8162,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4762,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8162,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 4762,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T13:51:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4509,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44425,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 147,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T14:07:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4512,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44426,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 147,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T14:07:34+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 4515,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44426,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 147,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T14:22:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4518,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44427,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 148,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T14:22:34+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 4521,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44427,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 148,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T14:53:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4524,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44428,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 91,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-10T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T14:53:34+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 4527,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44428,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 91,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T16:31:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4650,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T16:31:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4651,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 226,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 140,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T16:31:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4652,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T16:31:34+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4656,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T16:31:34+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4657,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T16:31:34+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 4658,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 86,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44430,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 226,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 140,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T17:55:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4678,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T17:55:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4679,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T17:55:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4680,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 982,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 498,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T17:55:34+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4854,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T17:55:34+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 4855,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 498,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-07T17:55:34+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 4856,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 484,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44431,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 982,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 498,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-08T11:13:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4740,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44432,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-08T11:13:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4741,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44432,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-08T11:13:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4742,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44432,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 402,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-08T11:51:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4834,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44433,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 127,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-08T11:51:34+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4835,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44433,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 127,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-10T12:00:07+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4876,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44436,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 132,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-10T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-10T12:00:07+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4877,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44436,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 92,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-10T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-10T12:00:07+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4878,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44436,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 132,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-10T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5638,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 341,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 341,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5639,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1484,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 961,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1825,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1302,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5639,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1484,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 961,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1825,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1302,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5640,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 961,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1302,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 341,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5640,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 961,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42507,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1302,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 341,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5632,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5633,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2908,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2308,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2908,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2308,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5634,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2308,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 140,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2308,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 140,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5635,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5636,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5637,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 310,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3475,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3165,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5637,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 310,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3475,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3165,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 62410,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 62411,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2908,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2308,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2908,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2308,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 62412,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2308,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 140,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2308,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 140,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 62413,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 140,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 62414,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3475,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3165,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3475,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3165,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T11:44:35+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 62415,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12104,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3165,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2908,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:06:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5720,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 829,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 811,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 829,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 811,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:06:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5721,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 811,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 811,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:06:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5722,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1245,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 829,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1245,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 829,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:06:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5723,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 170,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1415,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1245,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:06:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5723,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 170,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42530,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1415,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1245,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:07:25+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5724,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42003,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 647,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42003,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 647,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:07:25+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5725,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42003,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 153,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42003,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 800,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 647,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:07:25+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5725,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42003,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 153,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42003,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 800,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 647,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:15:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5758,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-21T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:15:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5759,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-21T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:15:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5760,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 938,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 469,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-21T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:15:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5760,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 938,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 469,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-21T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:15:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5761,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 938,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 469,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-21T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:15:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 5761,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 469,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42044,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 938,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 469,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-21T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:25:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5802,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12152,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 121,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 93,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:25:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5803,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12152,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 93,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:25:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5804,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12152,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 121,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 93,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:25:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5805,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12152,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 71,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5874,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 257,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 65,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5875,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 65,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5876,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 65,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5877,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5878,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1133,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1133,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5879,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5880,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 5881,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-20T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 11429,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 11430,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1133,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1133,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 11431,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 11432,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 11433,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 11435,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1777,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1624,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42809,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 153,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 11439,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1777,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1624,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42809,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 153,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-05-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 25818,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 25819,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1624,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 1303,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25820,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 170,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25821,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 170,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1133,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:47:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25822,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 170,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42072,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1303,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1133,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:57:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5929,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43090,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 111,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:57:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5930,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43090,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 100,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T12:57:25+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 5931,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43090,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 111,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T14:56:26+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6942,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1652,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1584,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T14:56:26+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 6943,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1790,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1652,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T14:56:26+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 6944,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1584,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1584,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T14:56:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6945,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1652,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1584,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1652,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1584,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T14:56:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6946,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3227,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1790,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3227,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1790,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-27T14:56:26+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 6947,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1790,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1652,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1790,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1652,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 7265,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 294,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 7266,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 294,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7267,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1162,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 160,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1002,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7268,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 160,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 560,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 400,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7268,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 160,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 560,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 400,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7269,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 694,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 294,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 400,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:19:46+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7270,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 694,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 294,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47743,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 400,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:22:46+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 7276,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47745,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 9,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-28T13:22:46+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7277,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47745,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1230,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47745,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1239,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 9,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-30T11:29:42+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 7280,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40918,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 51,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-30T11:29:42+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 54463,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40918,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 66,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-07-30T11:29:42+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 54473,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40918,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 66,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T12:41:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 7948,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40991,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 128,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:41:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 7960,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40992,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 359,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 7967,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1705,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1705,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7968,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 992,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1705,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 7968,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 992,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1705,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 35882,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 35883,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2687,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2687,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 37187,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37188,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2687,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37188,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2687,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37286,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2117,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2071,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 626,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 580,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37287,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1274,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1453,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1423,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37288,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1875,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1855,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 842,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 822,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37289,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2389,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2242,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 455,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 308,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37290,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2071,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1875,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 822,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 626,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37291,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2567,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2389,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 308,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 130,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37292,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 36,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 10,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2661,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37293,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 315,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 254,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2443,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2382,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37294,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1396,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1291,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1406,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1301,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37295,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 847,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 730,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1967,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1850,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37296,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 721,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 637,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2060,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1976,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37297,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2242,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2117,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 580,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 455,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37298,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 254,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 128,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2569,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2443,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37299,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 315,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2382,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2305,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37300,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 992,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1705,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1453,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37301,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37301,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37302,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 637,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 392,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2305,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2060,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37303,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 992,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 847,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1850,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1705,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37304,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 128,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 115,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2582,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2569,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37305,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 115,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 36,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2661,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2582,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37306,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1855,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1396,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1301,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 842,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37307,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2567,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 130,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37308,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1291,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1274,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1423,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1406,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 37309,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 730,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 721,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 1976,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1967,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 68779,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2687,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 68780,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2687,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:43:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 68780,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45656,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 2697,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2687,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:44:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 7972,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40990,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 329,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:44:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 48912,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40990,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 338,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 211,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:44:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 48913,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40990,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 211,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-11T15:44:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 48914,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40990,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 338,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 211,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:03:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8153,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40910,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 419,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:03:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8154,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40908,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 307,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:03:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8155,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40909,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 855,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:03:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 50487,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40910,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 154,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:03:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 50488,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40910,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 283,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 154,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:03:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 50489,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40910,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 283,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 154,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-02-03T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:04:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8158,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40989,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 131,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:05:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8162,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40988,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 282,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:06:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8169,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 132,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:06:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8170,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40986,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 207,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:08:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8184,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40801,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 437,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:08:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8185,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40907,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1065,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:08:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8186,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40906,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 766,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:08:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8187,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40800,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 186,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:12:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8199,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40985,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 362,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:12:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8200,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40983,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 467,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:12:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8201,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40984,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 226,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:14:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8212,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40980,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 328,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:14:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25761,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40980,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 148,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:14:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25817,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40980,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 652,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 148,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:14:29+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 25819,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40980,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 148,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40980,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 148,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8353,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 20001,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 86,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8354,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40802,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 232,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8355,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40803,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 89,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8356,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40802,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 232,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8357,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40805,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 81,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8358,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40804,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 208,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-12T13:29:29+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8359,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40805,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 81,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-05T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8946,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 225,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 225,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8947,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 451,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 225,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 451,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 225,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8948,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 451,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 225,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 451,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 225,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8949,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7058,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 6975,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7509,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 7426,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8949,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7058,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 6975,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7509,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 7426,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8950,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 745,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 443,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1196,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 894,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8950,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 745,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 443,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1196,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 894,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8951,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 862,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 745,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1313,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1196,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8951,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 862,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 745,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1313,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1196,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8952,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 443,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 894,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 451,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8952,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 443,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 894,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 451,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8953,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 443,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 894,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 451,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8953,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 443,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 894,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 451,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8954,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6975,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 862,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7426,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1313,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8954,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6975,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 862,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7426,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1313,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8955,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 745,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 443,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1196,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 894,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:05:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 8955,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 745,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 443,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 920,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1196,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 894,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8964,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8965,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8966,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8967,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 270,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 270,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 8968,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 67695,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 67696,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 86,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 86,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 67697,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 86,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 86,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 67698,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 270,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 270,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 67699,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 86,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 86,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 67700,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 86,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 86,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-13T14:20:57+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 67701,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 8,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 990,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 502,\n" +
    "        \"osa\": 101,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-04-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-26T14:57:10+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12249,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47777,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 295,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-26T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-26T14:57:10+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12250,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47777,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 295,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-26T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 12709,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3775,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3775,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 12710,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3775,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3775,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12711,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3925,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3775,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12711,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3925,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3775,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12712,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5895,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5869,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2120,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2094,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12712,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5895,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5869,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2120,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2094,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12713,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5495,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5440,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1720,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1665,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12713,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5495,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5440,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1720,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1665,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12714,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5495,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5440,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1720,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1665,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12714,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5495,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5440,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1720,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1665,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12715,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5869,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5495,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2094,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1720,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12715,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5869,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5495,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2094,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1720,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12716,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 201,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2612,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2411,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12717,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6171,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5895,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2396,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2120,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12717,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6171,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5895,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2396,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2120,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12718,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5869,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5495,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2094,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1720,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12718,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5869,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5495,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2094,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1720,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12719,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3925,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1665,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12719,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3925,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1665,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12720,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6186,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 6171,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2411,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2396,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12720,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6186,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 6171,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2411,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2396,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12721,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6186,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 6171,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2411,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2396,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12721,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6186,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 6171,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2411,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2396,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12722,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6171,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5895,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2396,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2120,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12722,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 6171,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5895,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2396,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2120,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12723,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 201,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2612,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2411,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12724,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3925,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1665,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12724,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5440,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3925,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 1665,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 150,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12725,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3925,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3775,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12725,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3925,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3775,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 150,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12726,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5895,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5869,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2120,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2094,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:26:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12726,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 5895,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5869,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 29,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2120,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2094,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12746,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 31,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12746,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 31,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12747,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 31,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12747,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 2755,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 31,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12748,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 9977,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8841,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 7222,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6086,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12749,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 9977,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8841,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 7222,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6086,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12750,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 8841,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2755,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 6086,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12751,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 8841,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2755,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 6086,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 32,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12766,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12767,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12782,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 85,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12783,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 85,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12784,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12784,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12785,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T13:54:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12785,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49977,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 210,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:09:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12793,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 369,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:09:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12794,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 369,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:09:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12813,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 239,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:09:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12814,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 239,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:09:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12815,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 606,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 239,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 369,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:09:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12816,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 606,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 239,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 369,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:30:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12826,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 194,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:30:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 12827,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 194,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:30:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13274,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 225,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 168,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 194,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 137,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:30:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13275,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 225,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 168,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 194,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 137,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:30:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13276,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 362,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 225,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 137,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T14:30:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13277,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 362,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 225,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 49979,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 137,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12938,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3506,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2176,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1330,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12939,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12939,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12940,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12940,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 158,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12941,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12941,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12942,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3506,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2176,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1330,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12943,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12943,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12944,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12944,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12945,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12945,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 490,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 320,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12946,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5223,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3506,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3047,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1330,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12947,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12947,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 320,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 158,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12948,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5223,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3506,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3047,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1330,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12949,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-27T15:54:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 12949,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 202,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 4,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2176,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 490,\n" +
    "        \"osa\": 201,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13070,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 3610,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 3610,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13071,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 3610,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 3610,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13072,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 609,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 4219,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3610,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13072,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 609,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 4219,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3610,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13073,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 14190,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 609,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 13581,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13074,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 14190,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 609,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 13581,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13075,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 609,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 4219,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3610,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T08:47:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13075,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 609,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 37,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 7,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 4219,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3610,\n" +
    "        \"osa\": 36,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T09:23:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 13106,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48437,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T09:23:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 13107,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48437,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 270,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T09:23:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13108,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48437,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 555,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 270,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48437,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 285,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T09:23:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13109,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48437,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 555,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 270,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48437,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 285,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-03T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13206,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 185,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 47,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 185,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 47,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13207,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 781,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 185,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 781,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 185,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13208,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 47,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 47,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13209,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 47,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 47,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13210,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5498,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 801,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5498,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 801,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13211,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 781,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 185,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 781,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 185,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13212,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 185,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 47,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 185,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 47,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13213,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 781,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 781,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13214,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 781,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 781,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T10:39:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13215,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5498,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 801,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 101,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5498,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 801,\n" +
    "        \"osa\": 7,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 13380,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1373,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1252,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 13381,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1373,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1252,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 13382,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1608,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1252,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 13383,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1608,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1252,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13384,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1140,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1140,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13385,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1170,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1140,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1170,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1140,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13386,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1252,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1170,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1252,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1170,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13387,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1170,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1140,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1170,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1140,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 13388,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1252,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1170,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1252,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1170,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13389,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1696,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1608,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1473,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1373,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T12:14:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 13390,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1696,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1608,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 749,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1473,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1373,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-13T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T14:34:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14012,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1947,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 173,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1948,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 173,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T14:34:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14013,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 173,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 173,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T14:34:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14014,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1947,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 173,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1948,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 173,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T14:34:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14015,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 173,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 173,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T14:34:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14016,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2121,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1947,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2122,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1948,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-28T14:34:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14017,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2121,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1947,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 48404,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2122,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1948,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-28T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T10:23:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14207,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49988,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 381,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 273,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T10:23:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14208,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49988,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 381,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 273,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T10:23:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14209,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49988,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 273,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T10:23:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14210,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49988,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 470,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 381,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T10:23:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14211,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49988,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 273,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T10:23:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14212,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49988,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 470,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 381,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 14580,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3086,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2754,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14581,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2754,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 771,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 2754,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 771,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14582,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 771,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 771,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14583,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7430,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7295,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7430,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7295,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14584,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7430,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7295,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7430,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 7295,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14585,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3086,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2754,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 3086,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2754,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-08-31T15:46:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14586,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7295,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3086,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 921,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 7295,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3086,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T09:40:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14607,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 2328,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2041,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T09:40:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14608,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 2041,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1850,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 2041,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1850,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T09:40:19+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 14609,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 1850,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 1850,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T09:40:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14610,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 3561,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2328,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 3535,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2328,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T09:40:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14611,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 2328,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2041,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3291,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 2328,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2041,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 14622,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 14623,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14624,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14625,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-08-31T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14651,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14652,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14653,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14654,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14686,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 295,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14687,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 295,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14688,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 909,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 295,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14689,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1299,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 909,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14690,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1299,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 909,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1004,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 614,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T13:43:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14691,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 909,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 295,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43744,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 614,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14773,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 428,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14774,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 821,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 428,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 14775,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 821,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 428,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14776,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 621,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 523,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 910,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 812,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14776,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 621,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 523,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 910,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 812,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14777,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 621,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 523,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 910,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 812,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14777,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 621,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 523,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 910,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 812,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14778,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 523,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 491,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 812,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 780,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14778,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 523,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 491,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 812,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 780,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14779,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 491,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 780,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 289,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14779,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 491,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 780,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 289,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14780,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 491,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 780,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 289,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14780,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 491,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 780,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 289,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14781,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 984,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 821,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 162,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14782,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 632,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 621,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 921,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 910,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14782,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 632,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 621,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 921,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 910,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14783,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1111,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 984,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 289,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 162,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14784,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 632,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 931,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 921,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14784,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 632,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 931,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 921,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14785,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1111,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 984,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 289,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 162,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14786,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 984,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 821,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 162,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14787,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 632,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 931,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 921,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14787,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 632,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 931,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 921,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14788,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 523,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 491,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 812,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 780,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14788,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 523,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 491,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 812,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 780,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14789,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 632,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 621,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 921,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 910,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-01T14:06:19+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 14789,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 632,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 621,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43502,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 921,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 910,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T09:18:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15101,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 57902,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 831,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T10:15:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15111,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45977,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 462,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T10:30:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15113,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45979,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 191,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T12:33:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15116,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45976,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 188,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-07T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T12:42:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15104,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 315,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T12:42:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15105,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 315,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-04T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T12:42:17+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15126,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 290,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T12:42:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 15127,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 605,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 290,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 315,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-07T12:42:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 15128,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 605,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 290,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45978,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 315,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-08T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:33:39+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 15220,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46602,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 139,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:47:39+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 15230,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46901,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 287,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 198,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:47:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15231,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46901,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 335,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 198,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:47:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15232,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46901,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 198,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46901,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 198,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:54:39+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15239,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46604,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 161,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46604,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 161,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:54:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 15240,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46604,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 300,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46604,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 461,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 161,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T13:54:39+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 15240,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46604,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 300,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46604,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 461,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 161,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T14:09:39+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15243,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 48567,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 81,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T16:33:44+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15248,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 99999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-11T16:33:44+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 15249,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 99999,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 99999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 108,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-11T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:11:33+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15506,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43211,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 142,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-14T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:11:33+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15507,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12119,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 846,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12119,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 846,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-14T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:11:33+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15508,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12119,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12119,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-14T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:11:33+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15509,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12119,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1620,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 846,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12119,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1620,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 846,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-14T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15525,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1156,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 920,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 15526,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1156,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 920,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15527,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 15528,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 19858,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-27T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 19859,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 920,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-27T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 19860,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1094,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 920,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1074,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 920,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-27T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 19861,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1094,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 920,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1074,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 920,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-27T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19862,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 63,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1156,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1074,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-27T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-14T14:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19863,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 63,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43510,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1156,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1074,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-27T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-15T15:15:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17147,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46513,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 463,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-15T15:15:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17148,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46513,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 1325,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 463,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46513,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 862,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-15T15:22:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17154,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 747,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 633,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-15T15:22:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17155,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 46523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 633,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 46523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 633,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:42:33+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17484,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11076,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 85,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:42:33+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17485,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11076,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5370,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 809,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11076,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5285,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 724,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:42:33+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17486,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11076,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 809,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 85,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11076,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 724,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:47:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17494,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42247,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 731,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 586,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:47:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17495,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42247,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 586,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42247,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 586,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:54:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17502,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2887,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2887,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:54:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17503,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1063,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3950,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2887,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T09:54:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17503,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1063,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12113,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3950,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2887,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:02:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17515,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3338,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3338,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:02:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17516,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2105,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3855,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1750,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:02:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17517,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5088,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3338,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1750,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:02:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17517,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 5088,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3338,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1861,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1750,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:10:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17533,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1840,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1871,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1748,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:10:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17534,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1840,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1894,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1748,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:10:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17535,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1840,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1748,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1840,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1748,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:10:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17536,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 1840,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 7223,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1894,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 1840,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 7200,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1871,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:23:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17558,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12121,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2264,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2170,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:23:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17559,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12121,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2170,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12121,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2170,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:23:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17560,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 12121,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2264,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2170,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 12121,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2264,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2170,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17611,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2075,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 60,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17612,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5419,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5195,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17613,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2095,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2075,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17614,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 60,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17615,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5195,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4979,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5195,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4979,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17616,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4979,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4979,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17617,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2075,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 60,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2075,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 60,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17618,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5419,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5195,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5419,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5195,\n" +
    "        \"osa\": 17,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17619,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2879,\n" +
    "        \"jatkuvuuskoodi\": 3,\n" +
    "        \"etaisyys\": 2378,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2879,\n" +
    "        \"jatkuvuuskoodi\": 3,\n" +
    "        \"etaisyys\": 2378,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17620,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2378,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2095,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2378,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2095,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17621,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2095,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2075,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2095,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2075,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-17T10:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17622,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 60,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 60,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 18,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-17T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:21:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17727,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43683,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 195,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:21:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17728,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43683,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 488,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43683,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 683,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 195,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17734,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43693,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 346,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 300,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:26:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17735,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43693,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 300,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43693,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 300,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17751,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 21523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 304,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 89,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17752,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 21523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 272,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 90,\n" +
    "        \"osa\": 67,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 21523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 272,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 90,\n" +
    "        \"osa\": 67,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:39:32+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17753,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 21523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 90,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 67,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 21523,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 90,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 67,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:41:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17169,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43111,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 258,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:41:32+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17170,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43111,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 258,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-15T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:41:32+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17759,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43111,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 258,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-18T13:41:32+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17760,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 43111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 258,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 43111,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 14,\n" +
    "        \"etaisyys_loppu\": 258,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-18T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:05:58+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17911,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49666,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 224,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:05:58+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17912,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49666,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 224,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:05:58+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17913,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49666,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 358,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 224,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:05:58+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17914,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 49666,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 358,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 224,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:08:58+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 17869,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1462,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:08:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17876,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-26T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:08:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17877,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 750,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1462,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-26T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:08:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17925,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 712,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 16987,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-27T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17967,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 940,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 940,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17968,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 940,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 642,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 940,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 642,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17969,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17970,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17971,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 17972,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17973,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3307,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1865,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6692,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 5250,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17974,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 9929,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6544,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3385,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17974,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 9929,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6544,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3385,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 17975,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1865,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5250,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3385,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 29885,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5123,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 940,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5123,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 940,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 29886,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 940,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 642,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 940,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 642,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 29887,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 29888,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 29889,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 29890,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 29891,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1421,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5123,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 29891,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1421,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5123,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 29892,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6692,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 5250,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6692,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 5250,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 29893,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5250,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 5250,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-11-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 54502,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 940,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 6544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 940,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 54503,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 940,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 642,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 940,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 642,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 54504,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 54505,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 642,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 405,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 54506,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 255,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:16:58+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 54507,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11337,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 405,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 255,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-03-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:27:13+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 17999,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 16597,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 1679,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1205,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:27:13+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 18000,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 16597,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 1205,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:27:13+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 18001,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16597,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 2294,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 16597,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 9,\n" +
    "        \"etaisyys_loppu\": 3973,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1679,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-25T13:28:13+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 18005,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16988,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1063,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-25T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 18026,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 202,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 18027,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 207,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 18028,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4558,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 360,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4553,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 355,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 18029,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 360,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 207,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 355,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 202,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 18030,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4658,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4558,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4653,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4553,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 19279,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 207,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 19280,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 826,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 360,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 19281,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 360,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 207,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19282,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 360,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 207,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 360,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 207,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19283,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4654,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4554,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4658,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4558,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19284,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 207,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 207,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19285,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4554,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 826,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 4558,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 826,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-09-29T10:04:14+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19286,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 826,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 360,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 826,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 360,\n" +
    "        \"osa\": 6,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-09-29T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-09T12:44:08+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 19275,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11363,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 8,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-09T12:44:08+03:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 19276,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11363,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 223,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 8,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-09T12:44:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19277,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11363,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3242,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2321,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11363,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3465,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2544,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-09T12:44:08+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 19278,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11363,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2321,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11363,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2544,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 223,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-15T11:30:52+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 19956,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42622,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2992,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42622,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2992,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-30T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-15T11:30:52+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 19957,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42622,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3042,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2992,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42622,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 3042,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2992,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-30T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-21T10:41:51+03:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 20362,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-21T10:41:51+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20367,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 82,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 82,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-21T10:41:51+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20368,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 236,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 82,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 236,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 82,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-21T10:41:51+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20369,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 236,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 99998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 801,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 236,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-02T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 20607,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 145,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 145,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 20608,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 145,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 145,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20609,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1732,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 733,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1877,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 878,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20609,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1732,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 733,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1877,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 878,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20610,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1873,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1745,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2018,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1890,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20610,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1873,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1745,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 2018,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1890,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20611,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 733,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 878,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 145,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20611,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 733,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 878,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 145,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20612,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1733,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1732,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1878,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1877,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20612,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1733,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1732,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1878,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1877,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20613,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1745,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1733,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1890,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1878,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-10-23T12:17:17+03:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20613,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1745,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1733,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 2241,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 1890,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1878,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-05T08:42:54+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 4853,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44435,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 85,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-07-09T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-05T08:42:54+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 20688,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 44435,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 85,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 44435,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 85,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:12:54+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 22163,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40958,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 888,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 495,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:12:54+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 22164,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40958,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 495,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:12:54+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 22165,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40958,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1518,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 888,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:12:54+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 22166,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40958,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 888,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 495,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:12:54+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 22167,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40958,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1518,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 888,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:36:54+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 22559,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42034,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 188,\n" +
    "        \"jatkuvuuskoodi\": 6,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42034,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 308,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 120,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:36:54+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 22560,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42034,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 188,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42034,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 308,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 120,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-09T10:36:54+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 22561,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 42034,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 308,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 188,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 42034,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 2,\n" +
    "        \"etaisyys_loppu\": 120,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24183,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 251,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1963,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24183,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 251,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1963,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24184,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1127,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 535,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2555,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1963,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24185,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 535,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3090,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2555,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24186,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 535,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3090,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2555,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24187,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1963,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 251,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24187,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1963,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 251,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1712,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24188,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 251,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1963,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-18T06:57:45+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 24188,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 251,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40922,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1963,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1712,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T07:21:39+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25844,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 118,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 3,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-25T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T07:21:39+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25845,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-25T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T07:21:39+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25846,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 577,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-25T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T07:21:39+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25881,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 15,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 15,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T07:21:39+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25882,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11111,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-11-26T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T14:05:39+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25989,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 18354,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3889,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 2253,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 18354,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 1636,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-11-26T14:05:39+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25990,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 18354,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 2253,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 18354,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 12,\n" +
    "        \"etaisyys_loppu\": 3889,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 1636,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-12-16T10:41:10+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 26228,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 45660,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 965,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 45660,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 965,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-10-01T00:00:00+03:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-12-31T08:39:10+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26251,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 70012,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 122,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 314,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 70012,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 122,\n" +
    "        \"jatkuvuuskoodi\": 2,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 314,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-12-31T09:14:10+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26264,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 73623,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 194,\n" +
    "        \"jatkuvuuskoodi\": 3,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 351,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 73623,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 194,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 351,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2020-12-31T09:14:10+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26265,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 73623,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 637,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 194,\n" +
    "        \"osa\": 351,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 73623,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 3,\n" +
    "        \"etaisyys_loppu\": 637,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 194,\n" +
    "        \"osa\": 351,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26615,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 284,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 284,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26616,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1522,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1478,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1522,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1478,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26617,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1462,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 484,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1462,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 484,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26618,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 37000,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 265,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 45,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 37000,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 265,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 45,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26619,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 47006,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 381,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 47006,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 381,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26620,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 11044,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6999,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 11044,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 6999,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26621,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 6999,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1522,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 6999,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1522,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26622,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 484,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 284,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 484,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 284,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:19:19+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26623,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1478,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1462,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 19,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 1478,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1462,\n" +
    "        \"osa\": 9,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-04T16:25:14+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 26626,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 69999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 29,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 99997,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 10,\n" +
    "        \"etaisyys_loppu\": 29,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T12:48:57+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 26641,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 17999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 124,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 37999,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 124,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:40:58+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26654,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 16998,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 46,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26637,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26638,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26655,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26656,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26691,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 17997,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 56,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26692,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 17996,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 83,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26693,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 17997,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 230,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26694,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 26695,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-05T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 41261,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 736,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 658,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-12-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 41262,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 804,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 658,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-12-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 41263,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 3,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 244,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 5\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-12-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 41264,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 658,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 658,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 244,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-12-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-05T15:55:58+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 41265,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1335,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 804,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 11641,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 736,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2019-12-16T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26893,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 2705,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2436,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26894,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 482,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 400,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26895,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 5604,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5001,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26896,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4482,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26897,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4482,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26898,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1935,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1380,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26899,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4482,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2705,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26900,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 879,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 493,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26901,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1380,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 879,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26902,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 2436,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1975,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26903,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4482,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2705,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26904,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 5001,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4502,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26905,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 493,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 482,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26906,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 493,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 482,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26907,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1975,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1935,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26908,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1380,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 879,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26909,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1935,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1380,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26910,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 2436,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1975,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26911,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 2705,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 2436,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26912,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 6290,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 5604,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26913,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 1975,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1935,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26914,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 879,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 493,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 26915,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 400,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-04T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26939,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4884,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 876,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26940,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 6693,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4884,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26941,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 876,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 26942,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4884,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 876,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 27061,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4884,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 876,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27064,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 382,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 382,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27065,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 494,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 382,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 876,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 764,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27066,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 6311,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4502,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 6693,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 4884,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-09T19:27:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27067,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4502,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 494,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 3,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 4,\n" +
    "        \"etaisyys_loppu\": 4884,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 876,\n" +
    "        \"osa\": 217,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27088,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27089,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27090,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 265,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3377,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27091,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3250,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 913,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 2729,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 392,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27092,\n" +
    "      \"kaannetty\": 1,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 913,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 265,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3377,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 2729,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-09T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 5,\n" +
    "      \"muutostunniste\": 27253,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1921,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 1319,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 27254,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 913,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 265,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 913,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 265,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 27255,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 10,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 27256,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1319,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 913,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 1319,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 913,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 27257,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 265,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 265,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 27258,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 276,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 265,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 276,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 265,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27259,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27260,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3250,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1515,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3250,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 1515,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27261,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3367,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3367,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27262,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3300,\n" +
    "        \"jatkuvuuskoodi\": 4,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 1,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3300,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-10T12:10:20+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27263,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 41011,\n" +
    "        \"ajorata\": 2,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 1,\n" +
    "        \"etaisyys_loppu\": 3642,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 3250,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2022-02-10T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:43:42+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8211,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 192,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:43:42+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25760,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 196,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:43:42+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25816,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 71,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:43:42+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25821,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 267,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 71,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 196,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:43:42+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27584,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 192,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 192,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-19T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:43:42+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27584,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 192,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40982,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 192,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-19T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 8213,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25762,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 325,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-01T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 2,\n" +
    "      \"muutostunniste\": 25818,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 104,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 0,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 0,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 0,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 25820,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 429,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 104,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 1,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 325,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 1\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2020-01-02T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 1,\n" +
    "      \"muutostunniste\": 27585,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 138,\n" +
    "        \"jatkuvuuskoodi\": 5,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-19T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27586,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 254,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 138,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-19T00:00:00+02:00\"\n" +
    "    },\n" +
    "    {\n" +
    "      \"laatimisaika\": \"2021-02-19T18:44:41+02:00\",\n" +
    "      \"muutostyyppi\": 3,\n" +
    "      \"muutostunniste\": 27586,\n" +
    "      \"kaannetty\": 0,\n" +
    "      \"kohde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 254,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 0,\n" +
    "        \"osa\": 2,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"lahde\": {\n" +
    "        \"tie\": 40981,\n" +
    "        \"ajorata\": 0,\n" +
    "        \"hallinnollinen_luokka\": 2,\n" +
    "        \"ely\": 8,\n" +
    "        \"etaisyys_loppu\": 392,\n" +
    "        \"jatkuvuuskoodi\": 1,\n" +
    "        \"etaisyys\": 138,\n" +
    "        \"osa\": 1,\n" +
    "        \"tietyyppi\": 3\n" +
    "      },\n" +
    "      \"muutospaiva\": \"2021-02-19T00:00:00+02:00\"\n" +
    "    }\n" +
    "  ]\n" +
    "}"



var server = http.createServer(function (req, res) {
    console.log("server");
    if (req.method == 'GET') {
            res.writeHead(200, {'Content-Type': 'text/json'});
            res.end(dataOutide);
      
    } 
});

var host = process.argv[2];
var port = process.argv[3];
console.log(util.format('Starting echoing viite test server on %s in port %d', host, port));
server.listen(port, host);

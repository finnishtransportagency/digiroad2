(function(root) {
  root.EnumeratedPropertyValuesTestData = {
    generate: function() {
      return [
        {
          propertyId: 0,
          publicId: "vaikutussuunta",
          propertyName: "Vaikutussuunta",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "2",
              propertyDisplayValue: "Digitointisuuntaan"
            },
            {
              propertyValue: "3",
              propertyDisplayValue: "Digitointisuuntaa vastaan"
            }
          ]
        },
        {
          propertyId: 300021,
          publicId: "sahkoinen_aikataulunaytto",
          propertyName: "Sähköinen aikataulunäyttö",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            }
          ]
        },
        {
          propertyId: 300025,
          publicId: "valaistus",
          propertyName: "Valaistus",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            }
          ]
        },
        {
          propertyId: 300017,
          publicId: "pyorateline",
          propertyName: "Pyöräteline",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            }
          ]
        },
        {
          propertyId: 300005,
          publicId: "aikataulu",
          propertyName: "Aikataulu",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            }
          ]
        },
        {
          propertyId: 300029,
          publicId: "saattomahdollisuus_henkiloautolla",
          propertyName: "Saattomahdollisuus henkilöautolla",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            }
          ]
        },
        {
          propertyId: 300009,
          publicId: "mainoskatos",
          propertyName: "Mainoskatos",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            }
          ]
        },
        {
          propertyId: 300,
          publicId: "tietojen_yllapitaja",
          propertyName: "Tietojen ylläpitäjä",
          propertyType: "single_choice",
          required: true,
          values: [
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tiedossa"
            },
            {
              propertyValue: "3",
              propertyDisplayValue: "Helsingin seudun liikenne"
            },
            {
              propertyValue: "4",
              propertyDisplayValue: "Liikennevirasto"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Kunta"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "ELY-keskus"
            }
          ]
        },
        {
          propertyId: 300013,
          publicId: "penkki",
          propertyName: "Penkki",
          propertyType: "single_choice",
          required: false,
          values: [
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            }
          ]
        },
        {
          propertyId: 200,
          publicId: "pysakin_tyyppi",
          propertyName: "Pysäkin tyyppi",
          propertyType: "multiple_choice",
          required: true,
          values: [
            {
              propertyValue: "5",
              propertyDisplayValue: "Virtuaalipysäkki"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Raitiovaunu"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Linja-autojen paikallisliikenne"
            },
            {
              propertyValue: "3",
              propertyDisplayValue: "Linja-autojen kaukoliikenne"
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            },
            {
              propertyValue: "4",
              propertyDisplayValue: "Linja-autojen pikavuoro"
            }
          ]
        },
        {
          propertyId: 100,
          publicId: "katos",
          propertyName: "Katos",
          propertyType: "single_choice",
          required: true,
          values: [
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa"
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei"
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä"
            }
          ]
        }
      ];
    }
  };
}(this));

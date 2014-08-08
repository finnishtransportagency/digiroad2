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
              propertyDisplayValue: "Digitointisuuntaan",
              imageId: null
            },
            {
              propertyValue: "3",
              propertyDisplayValue: "Digitointisuuntaa vastaan",
              imageId: null
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
              propertyDisplayValue: "Ei tietoa",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei",
              imageId: null
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
              propertyDisplayValue: "Ei tietoa",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei",
              imageId: null
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
              propertyDisplayValue: "Ei",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa",
              imageId: null
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
              propertyDisplayValue: "Ei tietoa",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
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
              propertyDisplayValue: "Ei",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa",
              imageId: null
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
              propertyDisplayValue: "Ei",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa",
              imageId: null
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
              propertyDisplayValue: "Ei tiedossa",
              imageId: null
            },
            {
              propertyValue: "3",
              propertyDisplayValue: "Helsingin seudun liikenne",
              imageId: null
            },
            {
              propertyValue: "4",
              propertyDisplayValue: "Liikennevirasto",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Kunta",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "ELY-keskus",
              imageId: null
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
              propertyDisplayValue: "Ei tietoa",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei",
              imageId: null
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
              propertyDisplayValue: "Virtuaalipysäkki",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Raitiovaunu",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Linja-autojen paikallisliikenne",
              imageId: null
            },
            {
              propertyValue: "3",
              propertyDisplayValue: "Linja-autojen kaukoliikenne",
              imageId: null
            },
            {
              propertyValue: "99",
              propertyDisplayValue: "Ei tietoa",
              imageId: null
            },
            {
              propertyValue: "4",
              propertyDisplayValue: "Linja-autojen pikavuoro",
              imageId: null
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
              propertyDisplayValue: "Ei tietoa",
              imageId: null
            },
            {
              propertyValue: "1",
              propertyDisplayValue: "Ei",
              imageId: null
            },
            {
              propertyValue: "2",
              propertyDisplayValue: "Kyllä",
              imageId: null
            }
          ]
        }
      ];
    }
  };
}(this));

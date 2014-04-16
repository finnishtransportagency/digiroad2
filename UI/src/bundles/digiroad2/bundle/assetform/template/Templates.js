Oskari.clazz.define('Oskari.digiroad2.bundle.assetform.template.Templates',
    function () {
        _.templateSettings = {
            interpolate: /\{\{(.+?)\}\}/g
        };

        this.featureDataWrapper = _.template('<div class="featureAttributesHeader">{{header}}</div>' +
            '<div class="featureAttributesWrapper">' +
                '<div class="streetView">{{streetView}}</div>' +
                '<div class="formContent">{{attributes}}</div>' +
            '</div>' +
            '<div class="formControls">{{controls}}</div>');

        this.streetViewTemplate  = _.template(
            '<a target="_blank" href="http://maps.google.com/?ll={{wgs84Y}},{{wgs84X}}&cbll={{wgs84Y}},{{wgs84X}}&cbp=12,{{heading}}.09,,0,5&layer=c&t=m">' +
                '<img alt="Google StreetView-näkymä" src="http://maps.googleapis.com/maps/api/streetview?key=AIzaSyBh5EvtzXZ1vVLLyJ4kxKhVRhNAq-_eobY&size=360x180&location={{wgs84Y}}' +
                ', {{wgs84X}}&fov=110&heading={{heading}}&pitch=-10&sensor=false">' +
                '</a>');

        this.featureDataTemplate = _.template('<div class="formAttributeContentRow">' +
            '<div class="formLabels">{{localizedName}}</div>' +
            '<div class="formAttributeContent">{{propertyValue}}</div>' +
            '</div>');

        this.featureDataTemplateText = _.template('<div class="formAttributeContentRow">' +
            '<div class="formLabels">{{localizedName}}</div>' +
            '<div class="formAttributeContent">' +
            '<input class="featureAttributeText" type="text"' +
            ' data-publicId="{{publicId}}" name="{{publicId}}"' +
            ' value="{{propertyDisplayValue}}">' +
            '</div>' +
            '</div>');

        this.featureDataTemplateButton = _.template('<div class="formAttributeContentRow">' +
            '<div class="formLabels">{{localizedName}}</div>' +
            '<div class="formAttributeContent">' +
            '<button class="featureAttributeButton"' +
            ' data-publicId="{{publicId}}" name="{{publicId}}"' +
            ' value="{{propertyValue}}">Vaihda suuntaa' +
            '</button>' +
            '</div>' +
            '</div>');

        this.featureDataTemplateLongText = _.template('<div class="formAttributeContentRow">' +
            '<div class="formLabels">{{localizedName}}</div>' +
            '<div class="formAttributeContent">' +
            '<textarea class="featureAttributeLongText"' +
            ' data-publicId="{{publicId}}" name="{{publicId}}">' +
            '{{propertyDisplayValue}}</textarea>' +
            '</div>' +
            '</div>');
        this.featureDataTemplateReadOnlyText = _.template('<div class=" formAttributeContentRow readOnlyRow">{{localizedName}}: {{propertyDisplayValue}}</div>');
        this.featureDataTemplateDate = _.template('<div class="formAttributeContentRow">' +
            '<div class="formLabels">{{localizedName}}</div>' +
            '<div class="formAttributeContent">' +
            '<input class="featureAttributeDate" type="text"' +
            ' data-publicId="{{publicId}}" name="{{publicId}}"' +
            ' value="{{propertyDisplayValue}}"/>' +
            '</div>' +
            '</div>');
        this.featureDataTemplateNA = _.template('<div class="formAttributeContentRow">' +
            '<div class="formLabels">{{localizedName}}</div>' +
            '<div class="featureAttributeNA">{{propertyValue}}</div>' +
            '</div>');

        this.featureDataTemplateChoice = _.template('<option {{selectedValue}} value="{{propertyValue}}">{{propertyDisplayValue}}</option>');

        this.featureDataTemplateCheckbox = _.template('<input {{checkedValue}} type="checkbox" value="{{propertyValue}}"></input><label for="{{name}}">{{propertyDisplayValue}}</label><br/>');

        this.featureDataControls = _.template('<button class="cancel">Peruuta</button><button class="save">Luo</button>');

        this.featureDataEditControls = _.template('<button class="cancel">Peruuta</button><button class="save">Tallenna</button>');
    }
);
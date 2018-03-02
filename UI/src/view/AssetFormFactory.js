(function(root) {

  var DynamicField = function () {
    var me = this;

    me.viewModeRender = function (field, currentValue) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-static">' + _value + '</p>' +
        '</div>'
      );
    };
    me.editModeRender = function (field, currentValue, setValue, asset){};

    me.inputElementHandler = function(assetTypeConfiguration, inputValue, field, setValue, asset){

      if(!asset)
        asset = assetTypeConfiguration.selectedLinearAsset.get()[0];

      var value = asset.value;

      if(!value)
        value = { properties: [] };

      var properties = _.find(value.properties, function(property){ return property.publicId === field.publicId; });
      var propertyValue = [];

      if(!_.isArray(inputValue))
        propertyValue.push(inputValue);

      else{
        propertyValue = inputValue;
      }

      if(properties){
        properties.value = propertyValue;
      }else
      {
        value.properties.push({
          publicId: field.publicId,
          propertyType: field.type,
          value: propertyValue
        });
      }
      setValue(value);
    };
  };

  var TextualField = function(assetTypeConfiguration){
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : undefined;
      var disabled = _.isUndefined(_value) ? 'disabled' : '';
      var element = $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <textarea name="' + field.publicId + '" class="form-control' + className + '" ' + disabled + '>' + value  + '</textarea>' +
        '</div>');

      element.find('textarea').on('keyup', function(){
         me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });
      return element;
    };
  };

  var NumericalField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : undefined;
      var disabled = _.isUndefined(_value) ? 'disabled' : '';
      var element =   $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <input type="text" name="' + field.publicId + '" class="form-control" id="' + className + '" '+ disabled+'>' +
        '</div>');

      element.find('input').on('keyup', function(){
        var disabled = isNaN($(this).val());
        $(this).closest("#feature-attributes").find(".save.btn").attr('disabled', disabled);

        if(!disabled)
          me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });
      return element;
    };
  };

  var SingleChoiceField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;
    var className = assetTypeConfiguration.className;
    var possibleValues = assetTypeConfiguration.possibleValues;
    var unit = assetTypeConfiguration.unit ? assetTypeConfiguration.unit : '';

    var template =  _.template(
      '<div class="form-group">' +
      '<label class="control-label">'+ className+'</label>' +
      '  <select <%- disabled %> class="form-control <%- className %>" name ="<%- name %>"><%= optionTags %> </select>' +
      '</div>');


    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : undefined;
      var disabled = _.isUndefined(_value) ? 'disabled' : '';

      var optionTags = _.map(possibleValues, function(value) {
        var selected = value === _value ? " selected" : "";
        return '<option value="' + value + '"' + selected + '>' + value + ' ' + unit + '</option>';
      }).join('');


      var element = $(template({className: className, optionTags: optionTags, disabled: disabled, name: field.publicId}));
      element.find('select').on('change', function(){
        me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });

      return element;

    };
  };

  var MultiSelectField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;
    var className = assetTypeConfiguration.className;
    var possibleValues = assetTypeConfiguration.possibleValues;

    var template =  _.template(
      '<div class="form-group">' +
      '<label class="control-label">'+ className+'</label>' +
      '<div class="choice-group"> ' +
      ' <%= divCheckBox %>' +
      '</div>'+
      '</div>');


    me.editModeRender = function (field, currentValue, setValue, asset) {

      var disabled = _.isEmpty(currentValue) ? 'disabled' : '';

      var divCheckBox = _.map(possibleValues, function(value) {
        return '' +
          '<div class = "checkbox">' +
          ' <label>'+ value + '<input type = "checkbox" class="multiChoice" name = "'+field.publicId+'" value="'+value+'"'+ disabled+'></label>' +
          '</div>';
      }).join('');
      var element =  $(template({divCheckBox: divCheckBox}));

      element.find('input').on('click', function(){
        var val = [];
        $('.multiChoice:checked').each(function(i){
            val[i] = $(this).val();
          });
        me.inputElementHandler(assetTypeConfiguration, val, field, setValue, asset);
      });

      return element;
    };

    me.viewModeRender = function (field, currentValue) {
      var template = _.template('<div class="form-group">' +
        '   <label class="control-label">' + className + '</label>' +
        '   <p class="form-control-static">' +
        ' <%= divCheckBox %>  ' +
        '</p>' +
        '</div>' );


      var values =  _.map(currentValue, function (values) { return values.value ; });
      return $(template({divCheckBox : values}));

    };
  };

  var DateField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;

    //TODO: Has to be possible to only add one field, or more than 3!
    me.editModeRender = function (field, currentValue, setValue, asset) {
      var html = $('' +
        '<div class="form-group">' +
        '<label class="control-label">' + me.className + '</label>' +
        '</div>');

      var dates = ['viimeinen_voimassaolopaiva', 'ensimmainen_voimassaolopaiva', 'inventointipaiva'];
      var elements = _.map(dates, function(date) {
        return $('<input type="text"/>').addClass('form-control').attr('id', date).attr('placeholder',"pp.kk.vvvv").on('keyup datechange', _.debounce(function (target) {
          // tab press
          if (target.keyCode === 9) {
            return;
          }
          var propertyValue = _.isEmpty(target.currentTarget.value) ? '' : dateutil.finnishToIso8601(target.currentTarget.value);

        }, 500));
      });
      return html.append(elements);
    };
    me.viewModeRender = function (field, currentValue) {
      var first = _.first(currentValue, function(values) { return values.value ; });

      var value =  first ? first.value : '';
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + me.className + '</label>' +
        '   <p class="form-control-static">' + value + '</p>' +
        '</div>'
      );
    };
  };

  root.AssetFormFactory = function (formStructure) {
    var me = this;
    var _assetTypeConfiguration;

    me.initialize = function(assetTypeConfiguration){
      var rootElement = $('#feature-attributes');
      _assetTypeConfiguration = assetTypeConfiguration;

      eventbus.on(events('selected', 'cancelled'), function () {
        rootElement.html(me.renderForm(assetTypeConfiguration.selectedLinearAsset));
        addDatePickers();

        if (assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
          me.bindEvents(rootElement, assetTypeConfiguration, 'a');
          me.bindEvents(rootElement, assetTypeConfiguration, 'b');
        } else {
          me.bindEvents(rootElement, assetTypeConfiguration);
        }
      });

      eventbus.on(events('unselect'), function() {
        rootElement.empty();
      });

      eventbus.on('layer:selected', function(layer) {
        if(assetTypeConfiguration.isVerifiable && assetTypeConfiguration.layerName === layer){
          renderLinktoWorkList(layer);
        }
        else {
          $('#information-content .form[data-layer-name="' + assetTypeConfiguration.layerName +'"]').remove();
        }
      });

      eventbus.on('application:readOnly', function(readOnly){
        if(assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer() && assetTypeConfiguration.selectedLinearAsset.count() !== 0) {
          rootElement.html(me.renderForm(assetTypeConfiguration.selectedLinearAsset));
          rootElement.find('.read-only-title').toggle(readOnly);
          rootElement.find('.edit-mode-title').toggle(!readOnly);
          me.bindEvents(rootElement, assetTypeConfiguration);
        }
      });

      eventbus.on(events('valueChanged'), function(selectedLinearAsset) {
        rootElement.find('.form-controls.linear-asset button.save').attr('disabled', !selectedLinearAsset.isSaveable());
        rootElement.find('.form-controls.linear-asset button.cancel').attr('disabled', false);
        rootElement.find('.form-controls.linear-asset button.verify').attr('disabled', selectedLinearAsset.isSaveable());
      });

      function events() {
        return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
      }
    };

    me.renderElements = function(selectedAsset, isReadOnly, setAsset, asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var availableFieldTypes = [
        {name: 'text', field: new TextualField(assetTypeConfiguration)},
        {name: 'singleChoice', field: new SingleChoiceField(assetTypeConfiguration)},
        {name: 'datePicker', field: new DateField(assetTypeConfiguration)},
        {name: 'multipleChoice', field: new MultiSelectField(assetTypeConfiguration)},
        {name: 'number', field: new NumericalField(assetTypeConfiguration)}
      ];

      formStructure.fields.sort(function (a, b) { return a.weigth - b.weight; });
      var fieldGroupElement = $('<div class = "input-unit-combination" >');
      _.each(formStructure.fields, function (field) {
        var values = [];
        if (selectedAsset.get().value) {
          values = _.find(selectedAsset.properties, function (property) { return property.publicId === field.publicId; }).values;
        }
        var fieldType = _.find(availableFieldTypes, function (availableFieldType) { return availableFieldType.name === field.type; }).field;
        var fieldElement = isReadOnly ? fieldType.viewModeRender(field, values, setAsset) : fieldType.editModeRender(field, values, setAsset, asset);
        fieldGroupElement.append(fieldElement);

      });
      return fieldGroupElement;
    };

    me.renderForm = function (selectedAsset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var isReadOnly = applicationModel.isReadOnly() || validateAdministrativeClass(selectedAsset, assetTypeConfiguration.editConstrains);
      var asset = selectedAsset.get();

      var body = createBody(selectedAsset);

      if(selectedAsset.isSplitOrSeparated()) {

        var sideCodeClassA = generateClassName('a');
        var radioA = singleValueEditElement(asset[0].value,  assetTypeConfiguration, 'a');
        body.find('.form').append(radioA);
        body.find('.form-editable-' + sideCodeClassA ).append(me.renderElements(selectedAsset, isReadOnly, selectedAsset.setAValue, asset[0]));

        var sideCodeClassB = generateClassName('b');
        var radioB = singleValueEditElement(asset[1].value,  assetTypeConfiguration, 'b');
        body.find('.form').append(radioB);
        body.find('.form-editable-' + sideCodeClassB).append(me.renderElements(selectedAsset, isReadOnly, selectedAsset.setBValue, asset[1]));
      }
      else
      {
        var sideCodeClass = generateClassName('');
        var radio = singleValueEditElement(asset[0].value, assetTypeConfiguration);
        body.find('.form').append(radio);
        body.find('.form-editable-' + sideCodeClass).append(me.renderElements(selectedAsset, isReadOnly, selectedAsset.setValue));
      }
      addBodyEvents(body, assetTypeConfiguration, isReadOnly);
      return body;
    };

    function singleValueEditElement(currentValue, assetTypeConfiguration, sideCode) {
      var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
      var withValue = _.isUndefined(currentValue) ? '' : 'checked';

      var unit = assetTypeConfiguration.unit ? currentValue ? currentValue + ' ' + assetTypeConfiguration.unit : '-' : currentValue ? 'on' : 'ei ole';

      var formGroup = $('' +
                      '<div class="form-group editable form-editable-'+ generateClassName(sideCode) +'">' +
                      '  <label class="control-label">' + assetTypeConfiguration.editControlLabels.title + '</label>' +
                      '  <p class="form-control-static ' + assetTypeConfiguration.className + '" style="display:none;">' + unit.replace(/[\n\r]+/g, '<br>') + '</p>' +
                      '</div>');

      formGroup.append($('' +
        sideCodeMarker(sideCode) +
        '<div class="edit-control-group choice-group">' +
        '  <div class="radio">' +
        '    <label>' + assetTypeConfiguration.editControlLabels.disabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="disabled" ' + withoutValue + '/>' +
        '    </label>' +
        '  </div>' +
        '  <div class="radio">' +
        '    <label>' + assetTypeConfiguration.editControlLabels.enabled +
        '      <input ' +
        '      class="' + generateClassName(sideCode) + '" ' +
        '      type="radio" name="' + generateClassName(sideCode) + '" ' +
        '      value="enabled" ' + withValue + '/>' +
        '    </label>' +
        '  </div>' +
        '</div>'));

      return formGroup;
    }

    function sideCodeMarker(sideCode) {
      if (_.isUndefined(sideCode)) {
        return '';
      } else {
        return '<span class="marker">' + sideCode + '</span>';
      }
    }

    function createBody(asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var info = {
        modifiedBy :  asset.getModifiedBy() || '-',
        modifiedDate : asset.getModifiedDateTime() ? ' ' + asset.getModifiedDateTime() : '',
        createdBy : asset.getCreatedBy() || '-',
        createdDate : asset.getCreatedDateTime() ? ' ' + asset.getCreatedDateTime() : '',
        verifiedBy : asset.getVerifiedBy(),
        verifiedDateTime : asset.getVerifiedDateTime()
      };

      var verifiedFields = function() {
        return (asset.isVerifiable && info.verifiedBy && info.verifiedDateTime) ?
          '<div class="form-group">' +
          '   <p class="form-control-static asset-log-info">Tarkistettu: ' + info.verifiedBy + ' ' + info.verifiedDateTime + '</p>' +
          '</div>' : '';
      };

      var disabled = asset.isDirty() ? '' : 'disabled';
      var visible = (assetTypeConfiguration.isVerifiable && !_.isNull(asset.getId()) && asset.count() === 1);

      var saveButton = '<button class="save btn btn-primary" disabled> Tallenna</button>';
      var cancelButton = '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>';
      var verifiableButton = visible ? '<button class="verify btn btn-primary">Merkitse tarkistetuksi</button>' : '';

      var headerButtons = [saveButton, cancelButton, verifiableButton].join('');
      var footerButtons = [saveButton, cancelButton].join('');

      var toSeparateButton =  function() {
        return asset.isSeparable() ?
          '<div class="form-group editable">' +
          '  <label class="control-label"></label>' +
          '  <button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
          '</div>' : '';
      };

      var title = function () {
        if(asset.isUnknown() || asset.isSplit()) {
          return '<span class="read-only-title" style="display: block">' +assetTypeConfiguration.title + '</span>' +
            '<span class="edit-mode-title" style="display: block">' + assetTypeConfiguration.newTitle + '</span>';
        }
        return asset.count() === 1 ?
          '<span>Segmentin ID: ' + asset.getId() + '</span>' : '<span>' + assetTypeConfiguration.title + '</span>';

      };

      return  $('<header>' + title() + '<div class="linear-asset form-controls">' + headerButtons + '</div></header>' +
        '<div class="wrapper read-only">' +
        '   <div class="form form-horizontal form-dark">' +
        '     <div class="form-group">' +
        '       <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + info.createdBy  + info.createdDate + '</p>' +
        '     </div>' +
        '     <div class="form-group">' +
        '       <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + info.modifiedBy + info.modifiedDate + '</p>' +
        '     </div>' +
        verifiedFields() +
        '     <div class="form-group">' +
        '       <p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + asset.count() + '</p>' +
        '     </div>' +
        toSeparateButton() +
        '   </div>' +
        '</div>' +
        '<footer >' +
        '   <div class="linear-asset form-controls" style="display: none">' +
        footerButtons +
        '   </div> '+
        '</footer>') ;
    }

    me.bindEvents = function(rootELement, assetTypeConfiguration, sideCode) {
      var inputElement = rootELement.find('.form-editable-' + generateClassName(sideCode));
      var toggleElement = rootELement.find('.radio input.' + generateClassName(sideCode));
      var valueRemovers = {
        a: assetTypeConfiguration.selectedLinearAsset.removeAValue,
        b: assetTypeConfiguration.selectedLinearAsset.removeBValue
      };
      var removeValue = valueRemovers[sideCode] || assetTypeConfiguration.selectedLinearAsset.removeValue;
      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.find('.form-control, .choice-group .multiChoice').not('.edit-control-group.choice-group').attr('disabled', disabled);
        if (disabled) {
          removeValue();
        }
      });
    };

    function addBodyEvents(rootElement, assetTypeConfiguration, isReadOnly) {
      rootElement.find('.form-controls').toggle(!isReadOnly);
      rootElement.find('.editable .form-control-static').toggle(isReadOnly);
      rootElement.find('.editable .edit-control-group').toggle(!isReadOnly);
      rootElement.find('#separate-limit').toggle(!isReadOnly);

      rootElement.find('#separate-limit').on('click', function() { assetTypeConfiguration.selectedLinearAsset.separate(); });
      rootElement.find('.form-controls.linear-asset button.save').on('click', function() { assetTypeConfiguration.selectedLinearAsset.save(); });
      rootElement.find('.form-controls.linear-asset button.cancel').on('click', function() { assetTypeConfiguration.selectedLinearAsset.cancel(); });
      rootElement.find('.form-controls.linear-asset button.verify').on('click', function() { assetTypeConfiguration.selectedLinearAsset.verify(); });

      rootElement.find('.read-only-title').toggle(isReadOnly);
      rootElement.find('.edit-mode-title').toggle(!isReadOnly);
    }

    var addDatePickers = function () {
      //TODO: Should be able to have anothers datePickers! Needs to be added to dateutil a generic function!
      var $validFrom = $('#ensimmainen_voimassaolopaiva');
      var $validTo = $('#viimeinen_voimassaolopaiva');
      var $inventoryDate = $('#inventointipaiva');

      if ($validFrom.length > 0 && $validTo.length > 0) {
        dateutil.addDependentDatePickers($validFrom, $validTo, $inventoryDate);
      }
    };

    function generateClassName(sideCode) {
      return sideCode ? _assetTypeConfiguration.className + '-' + sideCode : _assetTypeConfiguration.className;
    }

    function validateAdministrativeClass(selectedLinearAsset, editConstrains){
      var selectedAssets = _.filter(selectedLinearAsset.get(), function (selected) {
        return editConstrains(selected);
      });
      return !_.isEmpty(selectedAssets);
    }

    var renderLinktoWorkList = function renderLinktoWorkList(layerName) {
      var textName;
      switch(layerName) {
        case "maintenanceRoad":
          textName = "Tarkistamattomien huoltoteiden lista";
          break;
        default:
          textName = "Vanhentuneiden kohteiden lista";
      }

      $('#information-content').append('' +
        '<div class="form form-horizontal" data-layer-name="' + layerName + '">' +
        '<a id="unchecked-links" class="unchecked-linear-assets" href="#work-list/' + layerName + '">' + textName + '</a>' +
        '</div>');
    };
  };
})(this);


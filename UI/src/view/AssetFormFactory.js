(function(root) {

  var DynamicField = function () {
    var me = this;

    me.viewModeRender = function (field, currentValue) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '-';
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

      if(!value || !value.properties)
        value = { properties: [] };

      var properties = _.find(value.properties, function(property){ return property.publicId === field.publicId; });

      var values = [];
      if(!_.isArray(inputValue))
        values.push({ value : inputValue });

      else
        _.forEach(inputValue, function(input){
          values.push({
            value : input
          });
        });

      if(properties) {
        properties.values = values;
      }
      else {
        value.properties.push({
          publicId: field.publicId,
          propertyType: field.type,
          values: values
        });
      }
      setValue(value);
    };
  };

  var TextualLongField = function(assetTypeConfiguration){
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(value) ? 'disabled' : '';
      var element = $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <textarea name="' + field.publicId + '" class="form-control ' + className + '" ' + disabled + '>' + _value  + '</textarea>' +
        '</div>');

      element.find('textarea').on('keyup', function(){
         me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });
      return element;
    };
  };

  var TextualField = function(assetTypeConfiguration){
    DynamicField.call(this);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(value) ? 'disabled' : '';
      var element = $('' +
          '<div class="form-group">' +
          '   <label class="control-label">' + field.label + '</label>' +
          '   <input type="text" name="' + field.publicId + '" class="form-control ' + className + '" ' + disabled  + ' value="' + _value + '" >' +
          '</div>');

      element.find('input[type=text]').on('keyup', function(){
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
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(value) ? 'disabled' : '';
      var element =   $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <input type="text" name="' + field.publicId + '" class="form-control" value="' + _value + '"  id="' + className + '" '+ disabled+'>' +
        '</div>');

      element.find('input').on('keyup', function(){
        var disabled = isNaN($(this).val());
        if(!disabled)
          me.inputElementHandler(assetTypeConfiguration, $(this).val(), field, setValue, asset);
      });
      return element;
    };
  };

  var IntegerField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(value) ? 'disabled' : '';

      var element =   $('' +
          '<div class="form-group">' +
          '   <label class="control-label">' + field.label + '</label>' +
          '   <input type="text" name="' + field.publicId + '" class="form-control" value="' + _value + '"  id="' + className + '" '+ disabled+'>' +
          '</div>');

      element.find('input[type=text]').on('keyup', function(){
        var disabled = isNaN($(this).val());
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

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var template =  _.template(
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label +'</label>' +
        '  <select <%- disabled %> class="form-control <%- className %>" name ="<%- name %>"><%= optionTags %> </select>' +
        '</div>');


      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(value) ? 'disabled' : '';
      var unit =  field.unit ? field.unit : '';
      var optionTags = _.map(field.values, function(value) {
        var selected = value.id === _value ? " selected" : "";
        return '<option value="' + value.id + '"' + selected + '>' + value.label + ' ' + unit + '</option>';
      }).join('');


      var element = $(template({className: className, optionTags: optionTags, disabled: disabled, name: field.publicId}));

      _.forEach(currentValue, function(current){
        element.find('option[value="'+current.value+'"]').attr('selected', true);
      });

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

    me.editModeRender = function (field, currentValue, setValue, asset) {

      var template =  _.template(
        '<div class="form-group">' +
        '<label class="control-label">'+ field.label+'</label>' +
        '<div class="choice-group"> ' +
        ' <%= divCheckBox %>' +
        '</div>'+
        '</div>');

      //var disabled = _.isEmpty(currentValue) ? 'disabled' : '';

      var divCheckBox = _.map(field.values, function(value) {
        return '' +
          '<div class = "checkbox">' +
          ' <label>'+ value.label + '<input type = "checkbox" class="multiChoice" name = "'+field.publicId+'" value="'+value.id+'"></label>' +
          '</div>';
      }).join('');
      var element =  $(template({divCheckBox: divCheckBox}));

      _.forEach(currentValue, function(current){
        element.find(':input[value="'+current.value+'"]').attr('checked', true);
      });

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

    me.editModeRender = function (field, currentValue, setValue, asset) {
      var value = _.first(currentValue, function(values) { return values.value ; });
      var _value = value ? value.value : '';
      var disabled = _.isUndefined(value) ? 'disabled' : '';

      var addDatePickers = function (field, html) {
        var $dateElement = html.find('#' + field.publicId);
        dateutil.addDependentDatePicker($dateElement);
      };

      var html = $('' +
        '<div class="form-group">' +
        '<label class="control-label">' + field.label + '</label>' +
        '</div>');

      var elements = $('<input type="text"/>').addClass('form-control').attr('id', field.publicId).attr('placeholder',"pp.kk.vvvv").attr('disabled', disabled).on('keyup datechange', _.debounce(function (target) {
          // tab press
          if (target.keyCode === 9) {
            return;
          }
          var propertyValue = _.isEmpty(target.currentTarget.value) ? '' : dateutil.finnishToIso8601(target.currentTarget.value);
          field.type = 'text';
          me.inputElementHandler(assetTypeConfiguration, propertyValue, field, setValue, asset);
        }, 500));

      html.append(elements);
      addDatePickers(field, html);
      return html;
    };


    me.viewModeRender = function (field, currentValue) {
      var first = _.first(currentValue, function(values) { return values.value ; });

      var value =  first ? first.value : '';
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + field.label + '</label>' +
        '   <p class="form-control-static">' + value + '</p>' +
        '</div>'
      );
    };
  };

  var CheckboxField = function(assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;
    var className = assetTypeConfiguration.className;

    me.editModeRender = function (field, currentValue, setValue, asset) {

      var disabled = _.isEmpty(currentValue) ? 'disabled' : '';

      var element = $('' +
          '<div class="form-group">' +
          '<label class="control-label">'+ field.label+'</label>' +
          '<div class="choice-group"> ' +
          '<input type = "checkbox" class="multiChoice" name = "' + field.publicId + '"'+ disabled+'>' +
          '</div>'+
          '</div>');


      element.find("input[type=checkbox]").attr('checked', !_.isEmpty(currentValue));

      element.find('input[type=checkbox]').on('click', function(){
        var val = $('input[name=' + field.publicId + ']:checked').length;

        me.inputElementHandler(assetTypeConfiguration, val, field, setValue, asset);
      });

      return element;
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
        {name: 'long_text', field: new TextualLongField(assetTypeConfiguration)},
        {name: 'single_choice', field: new SingleChoiceField(assetTypeConfiguration)},
        {name: 'date', field: new DateField(assetTypeConfiguration)},
        {name: 'multiple_choice', field: new MultiSelectField(assetTypeConfiguration)},
        {name: 'integer', field: new IntegerField(assetTypeConfiguration)},
        {name: 'number', field: new NumericalField(assetTypeConfiguration)},
        {name: 'text', field: new TextualField(assetTypeConfiguration)},
        {name: 'checkbox', field: new CheckboxField(assetTypeConfiguration)}
      ];

      var fieldGroupElement = $('<div class = "input-unit-combination" >');
      _.each(_.sortBy(formStructure.fields, function(field){ return field.weight; }), function (field) {
        var values = [];
        if (selectedAsset.get()[0].value) {
          values = _.find(selectedAsset.get()[0].value.properties, function (property) { return property.publicId === field.publicId; }).values;
        }
        var fieldType = _.find(availableFieldTypes, function (availableFieldType) { return availableFieldType.name === field.type; }).field;
        var fieldElement = isReadOnly ? fieldType.viewModeRender(field, values, setAsset, asset) : fieldType.editModeRender(field, values, setAsset, asset);
        fieldGroupElement.append(fieldElement);

      });
      return fieldGroupElement;
    };

    me.renderForm = function (selectedAsset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var isReadOnly =  validateAdministrativeClass(selectedAsset, assetTypeConfiguration.editConstrains) || applicationModel.isReadOnly();
      var asset = selectedAsset.get();

      var created = createBody(selectedAsset);
      var body  = created.body;

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
      body.find('.form').append(created.separateButton);
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

      return {
        body: $('<header>' + title() + '<div class="linear-asset form-controls">' + headerButtons + '</div></header>' +
          '<div class="wrapper read-only">' +
          '   <div class="form form-horizontal form-dark">' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + info.createdBy + info.createdDate + '</p>' +
          '     </div>' +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + info.modifiedBy + info.modifiedDate + '</p>' +
          '     </div>' +
          verifiedFields() +
          '     <div class="form-group">' +
          '       <p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + asset.count() + '</p>' +
          '     </div>' +
          '   </div>' +
          '</div>' +
          '<footer >' +
          '   <div class="linear-asset form-controls" style="display: none">' +
          footerButtons +
          '   </div> ' +
          '</footer>'),
        separateButton: toSeparateButton
      };
    }

    me.bindEvents = function(rootELement, assetTypeConfiguration, sideCode) {
      var inputElement = rootELement.find('.form-editable-' + generateClassName(sideCode));
      var toggleElement = rootELement.find('.radio input.' + generateClassName(sideCode));
      var valueRemovers = {
        a: assetTypeConfiguration.selectedLinearAsset.removeAValue,
        b: assetTypeConfiguration.selectedLinearAsset.removeBValue
      };
      var valueSetters = {
        a: assetTypeConfiguration.selectedLinearAsset.setAValue,
        b: assetTypeConfiguration.selectedLinearAsset.setBValue
      };
      var removeValue = valueRemovers[sideCode] || assetTypeConfiguration.selectedLinearAsset.removeValue;
      var setValue = valueSetters[sideCode] ||  assetTypeConfiguration.selectedLinearAsset.setValue;
      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.find('.form-control, .choice-group .multiChoice').not('.edit-control-group.choice-group').attr('disabled', disabled);
        if (disabled) {
          removeValue();
        }
        else{
          // var value = _.isUndefined(assetTypeConfiguration.unit) ? defaultValue : inputElementValue(inputElement);
          var value = disabled ? 0 : 1;
          setValue(value);
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

    function generateClassName(sideCode) {
      return sideCode ? _assetTypeConfiguration.className + '-' + sideCode : _assetTypeConfiguration.className;
    }

    function validateAdministrativeClass(selectedLinearAsset, editConstrains){
      editConstrains = editConstrains || function() { return false; };

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


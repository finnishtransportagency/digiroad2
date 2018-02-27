(function(root) {

  var DynamicField = function (assetTypeConfiguration) {
    var me = this;

    me.viewModeRender = function (currentValue) {
      var value = _.first(currentValue, function(values) { return values.value ; }).value;
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + me.className + '</label>' +
        '   <p class="form-control-static">' + value + '</p>' +
        '</div>'
      );
    };
    me.editModeRender = function (currentValue) {

    };

    this.possibleValues = assetTypeConfiguration.possibleValues;

    this.unit = assetTypeConfiguration.unit ? assetTypeConfiguration.unit : '';

    this.className =  assetTypeConfiguration.className;
  };

  //TODO: Missing field function with validation for text, number...
  var TextualField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;

    me.editModeRender = function (currentValue) {
      // var value = _.first(currentValue, function(values) { return values.value ; }).value;
      var element = $('   <input type="text" class="form-control" id="' + me.className + '">');
      var html = $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + me.className + '</label>' +
        '</div>');

      html.append(element);
      bindEvents(element);
      return html;
    };

    var bindEvents = function (element) {
      element.bind('input', function(target){
        assetTypeConfiguration.selectedLinearAsset.setValue(target.currentTarget.value);
      });
    };
  };

  var SingleChoiceField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;

    var template =  _.template(
      '<div class="form-group">' +
      '<label class="control-label">'+me.className+'</label>' +
      '  <select <%- disabled %> class="form-control <%- className %>" ><%= optionTags %></select>' +
      '</div>');


    me.editModeRender = function (currentValue) {
      var firstValue = _.first(currentValue, function(values) { return values.value ; }).value;
      var optionTags = _.map(me.possibleValues, function(value) {
        var selected = value === firstValue ? " selected" : "";
        return '<option value="' + value + '"' + selected + '>' + value + ' ' + me.unit + '</option>';
      }).join('');
      var element =  $(template({className: me.className, optionTags: optionTags, disabled: ''}));
      bindEvents(element);
      return element;
    };

    var bindEvents = function(element) {
      var e =  $(element).find('.form-control').change(function(target){
        assetTypeConfiguration.selectedLinearAsset.setValue(target.currentTarget.value);
      });
    };
  };

  var MultiSelectField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;

    var template =  _.template(
      '<div class="form-group">' +
      '<label class="control-label">'+me.className+'</label>' +
      '<div class="choice-group"> ' +
      ' <%= divCheckBox %>' +
      '</div>'+
      '</div>');


    me.editModeRender = function (currentValue) {
      var firstValue = _.first(currentValue, function(values) { return values.value ; }).value;
      var inputElement = '';


      var divCheckBox = _.map(me.possibleValues, function(value) {
        return '' +
          '<div class = "checkbox">' +
          ' <label>'+ value + '<input type = "checkbox"></label>' +
          '</div>';
      }).join('');
      var element = $(template({divCheckBox: divCheckBox}));
      bindEvents(element);
      return element;

    };

    var bindEvents = function(element){
      $(element).find('input').change(function(target){
        assetTypeConfiguration.selectedLinearAsset.setValue(target.currentTarget.value);
      });
    };


    me.viewModeRender = function (currentValue) {
      var template = _.template('<div class="form-group">' +
        '   <label class="control-label">' + me.className + '</label>' +
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

    me.editModeRender = function (currentValue) {
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

    me.viewModeRender = function (currentValue) {
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
    this.disable = false;

    function setDisable(val) {
      this.disable = val;
    }

    function getDisable() {
      return  this.disable;
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

      function valueString(currentValue) {
        if (assetTypeConfiguration.unit) {
          return currentValue ? currentValue + ' ' + assetTypeConfiguration.unit : '-';
        } else {
          return currentValue ? 'on' : 'ei ole';
        }
      }

      var limitValueButtons = function() {
        var separateValueElement =
          singleValueElement( assetTypeConfiguration.selectedLinearAsset.getValue(), "a") +
          singleValueElement( assetTypeConfiguration.selectedLinearAsset.getValue(), "b");
        var valueElements =  assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated() ?
          separateValueElement :
          singleValueElement( assetTypeConfiguration.selectedLinearAsset.getValue());
        return '' +
          '<div class="form-elements-container">' +
          valueElements +
          '</div>';
      };

      function singleValueElement(currentValue, sideCode) {
        // if(Array.isArray(currentValue)){
        //   return '' +
        //     '<div class="form-group editable form-editable-'+ className +'">' +
        //     ' <label class="control-label">' + editControlLabels.title + '</label>' +
        //     ' <div class="form-control-static ' + className + '" style="display:none;">' +
        //     obtainFormControl(className, valueString, currentValue, possibleValues)  +
        //     ' </div>' +
        //     singleValueEditElement(currentValue, sideCode, measureInput(currentValue, generateClassName(sideCode), possibleValues)) +
        //     '</div>';
        //
        // }else {
        return '' +
          '<div class="form-group editable form-editable-'+ assetTypeConfiguration.className +'">' +
          '  <label class="control-label">' + assetTypeConfiguration.editControlLabels.title + '</label>' +
          '  <p class="form-control-static ' + assetTypeConfiguration.className + '" style="display:none;">' + valueString(currentValue).replace(/[\n\r]+/g, '<br>') + '</p>' +
          singleValueEditElement(currentValue, sideCode, assetTypeConfiguration) +
          '</div>';
        // }
      }

      function sideCodeMarker(sideCode) {
        if (_.isUndefined(sideCode)) {
          return '';
        } else {
          return '<span class="marker">' + sideCode + '</span>';
        }
      }

      function singleValueEditElement(currentValue, sideCode, assetTypeConfiguration) {
        var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
        var withValue = _.isUndefined(currentValue) ? '' : 'checked';

        if(_.isUndefined(currentValue))
          setDisable(true);

        return '' +
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
          '<div class = "input-unit-combination ' + generateClassName(sideCode) +'" > ' +
          '</div>' +
          '</div>';
      }

      var title = function () {
        if(asset.isUnknown() || asset.isSplit()) {
          return '<span class="read-only-title">' + asset.title + '</span>' ; //+
          //  TODO: check after with the toggleMode
          // '<span class="edit-mode-title">' + asset.newTitle + '</span>';
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
        limitValueButtons()+
        toSeparateButton() +
        '   </div>' +
        '</div>' +
        '<footer class="linear-asset form-controls" style="display: none">' +
        footerButtons +
        '</footer>') ;

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

    me.initialize = function(assetTypeConfiguration){
      var rootElement = $('#feature-attributes');
      _assetTypeConfiguration = assetTypeConfiguration;

      eventbus.on(events('selected', 'cancelled'), function () {
        //var properties = [inputs] -> formBody(asset, properties)
        rootElement.html(me.renderForm({
          getId: function(){ return 1; },
          count: function(){ return 1; },
          properties: [
            { publicId: 'HEIGHT', values:[{value: 2}] },
            { publicId: 'HEIGHT1', values:[{value: 70}] },
            { publicId: 'HEIGHT3',  values:[]},
            { publicId: 'HEIGHT4',  values:[{value: 80}, {value: 90}]}
          ],
          getModifiedBy : function () { },
          getModifiedDateTime : function () { },
          getCreatedBy : function () { },
          getCreatedDateTime : function () { },
          getVerifiedBy : function () { },
          getVerifiedDateTime : function () { },
          isDirty: function () { },
          isUnknown: function () { },
          isSplit: function () { },
          isSeparable : function () { return true; }
        }));

        addDatePickers();

        if (assetTypeConfiguration.selectedLinearAsset.isSplitOrSeparated()) {
          bindEvents(rootElement.find('.form-elements-container'), assetTypeConfiguration, 'a');
          bindEvents(rootElement.find('.form-elements-container'), assetTypeConfiguration, 'b');
        } else {
          bindEvents(rootElement.find('.form-elements-container'), assetTypeConfiguration);
        }

        rootElement.find('#separate-limit').on('click', function() { assetTypeConfiguration.selectedLinearAsset.separate(); });
        rootElement.find('.form-controls.linear-asset button.save').on('click', function() { assetTypeConfiguration.selectedLinearAsset.save(); });
        rootElement.find('.form-controls.linear-asset button.cancel').on('click', function() { assetTypeConfiguration.selectedLinearAsset.cancel(); });
        rootElement.find('.form-controls.linear-asset button.verify').on('click', function() { assetTypeConfiguration.selectedLinearAsset.verify(); });
      });
      eventbus.on(events('unselect'), function() {
        rootElement.empty();
      });

      //TODO: Only open form (renderForm) when asset is selected
      eventbus.on('application:readOnly', function(readOnly){
        if(assetTypeConfiguration.layerName ===  applicationModel.getSelectedLayer()) {
          rootElement.html(me.renderForm({
            getId: function(){ return 1; },
            count: function(){ return 1; },
            properties: [
              { publicId: 'HEIGHT', values:[{value: 1}] },
              { publicId: 'HEIGHT1', values:[{value: 80}] },
              { publicId: 'HEIGHT2', values:[{value: 0}] },
              { publicId: 'HEIGHT3',  values:[]},
              { publicId: 'HEIGHT4',  values:[{value: 80}, {value: 90}]}
            ],
            getModifiedBy : function () { },
            getModifiedDateTime : function () { },
            getCreatedBy : function () { },
            getCreatedDateTime : function () { },
            getVerifiedBy : function () { },
            getVerifiedDateTime : function () { },
            isDirty: function () { },
            isUnknown: function () { },
            isSplit: function () { },
            isSeparable : function () {}
          }));
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

    function generateClassName(sideCode) {
      return sideCode ? _assetTypeConfiguration.className + '-' + sideCode : _assetTypeConfiguration.className;
    }

    function bindEvents(rootElement, assetTypeConfiguration, sideCode) {
      var inputElement = rootElement.find('.input-unit-combination.' + generateClassName(sideCode));
      var toggleElement = rootElement.find('.radio input.' + generateClassName(sideCode));
      var valueSetters = {
        a: assetTypeConfiguration.selectedLinearAsset.setAValue,
        b: assetTypeConfiguration.selectedLinearAsset.setBValue
      };
      var setValue = valueSetters[sideCode] || assetTypeConfiguration.selectedLinearAsset.setValue;
      var valueRemovers = {
        a: assetTypeConfiguration.selectedLinearAsset.removeAValue,
        b: assetTypeConfiguration.selectedLinearAsset.removeBValue
      };
      var removeValue = valueRemovers[sideCode] || assetTypeConfiguration.selectedLinearAsset.removeValue;

      toggleElement.on('change', function(event) {
        var disabled = $(event.currentTarget).val() === 'disabled';
        inputElement.find(':input').attr('disabled', disabled);
        if (disabled) {
          removeValue();
        } else {
          //TODO: the send of this value should be checked
          var value = _.isUndefined(assetTypeConfiguration.unit) ? assetTypeConfiguration.defaultValue : inputElement.val();
          setValue(value);
        }
      });
    }


    me.renderForm = function (asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var isReadOnly = applicationModel.isReadOnly();  // || validateAdministrativeClass(selectedLinearAsset, editConstrains)

      var availableFieldTypes = [
        { name: 'text', field: new TextualField(assetTypeConfiguration) },
        { name: 'singleChoice', field: new SingleChoiceField(assetTypeConfiguration)},
        { name: 'datePicker', field: new DateField(assetTypeConfiguration)},
        { name: 'multiChoice', field: new MultiSelectField(assetTypeConfiguration)}
      ];
      var body = createBody(asset);

      formStructure.fields.sort(function(a,b) { return a.weigth - b.weight; });
      _.each(formStructure.fields, function(field){
        var values = [];
        if(asset){
          values = _.find(asset.properties, function(property){ return property.publicId === field.publicId; }).values;
        }
        var fieldType = _.find(availableFieldTypes, function(availableFieldType){ return availableFieldType.name === field.type; }).field;
        if(isReadOnly)
          body.find('.input-unit-combination').append(fieldType.viewModeRender(values));
        else
          body.find('.input-unit-combination').append(fieldType.editModeRender(values));

      });
      // body.find('.input-unit-combination :input').attr('disabled',true);
      return body ;
    };

    // function validateAdministrativeClass(selectedLinearAsset, editConstrains){
    //   var selectedAssets = _.filter(selectedLinearAsset.get(), function (selected) {
    //     return editConstrains(selected);
    //   });
    //   return !_.isEmpty(selectedAssets);
    // }
  };

})(this);


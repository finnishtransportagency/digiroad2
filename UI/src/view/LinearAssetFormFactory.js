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
      var value = _.first(currentValue, function(values) { return values.value ; }).value;
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + me.className + '</label>' +
        '   <input type="text" class="form-control" id="' + me.className + '">' +
        '</div>');
    };
    //
    // me.viewModeRender = function (currentValue) {
    //   var value = _.first(currentValue, function(values) { return values.value ; }).value;
    //   return $('' +
    //     '<div class="form-group">' +
    //     '   <label class="control-label">' + me.className + '</label>' +
    //     '   <p class="form-control-static">' + value + '</p>' +
    //     '</div>'
    //   );
    // };
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

      return $(template({className: me.className, optionTags: optionTags, disabled: ''}));

    };

    // me.viewModeRender = function (currentValue) {
    //   var firstValue = _.first(currentValue, function(values) { return values.value ; }).value;
    //   return $('' +
    //     '<div class="form-group">' +
    //     '   <label class="control-label">' + me.className + '</label>' +
    //     '   <p class="form-control-static">' + firstValue + '</p>' +
    //     '</div>'
    //   );
    // };
  };

  var BooleanField = function (assetTypeConfiguration) {
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;

    me.editModeRender = function (currentValue) {
      var withoutValue = _.isUndefined(currentValue) ? 'checked' : '';
      var withValue = _.isUndefined(currentValue) ? '' : 'checked';

      return $('<div class="form-group">' +
        '<div class="edit-control-group choice-group">' +
      '  <div class="radio">' +
      '    <label>' + assetTypeConfiguration.editControlLabels.disabled +
      '      <input ' +
      '      class="' + assetTypeConfiguration.className+ '" ' +
      '      type="radio" name="' + assetTypeConfiguration.className + '" ' +
      '      value="disabled" ' + withoutValue + '/>' +
      '    </label>' +
      '  </div>' +
      '  <div class="radio">' +
      '    <label>' + assetTypeConfiguration.editControlLabels.enabled +
      '      <input ' +
      '      class="' + assetTypeConfiguration.className + '" ' +
      '      type="radio" name="' + assetTypeConfiguration.className + '" ' +
      '      value="enabled" ' + withValue + '/>' +
      '    </label>' +
      '  </div>' +
      '  </div>');
    };
  };

  var DateField = function(assetTypeConfiguration){
    DynamicField.call(this, assetTypeConfiguration);
    var me = this;

    me.editModeRender = function (currentValue) {
      return $('' +
        '<div class="form-group">' +
        '   <label class="control-label">' + me.className + '</label>' +
        '  <input type="text" class="form-control" id="'+ me.className +'" placeholder="pp.kk.vvvv" aria-label="Use the arrow keys to pick a date">' +
        '</div>');
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

      var divCheckBox = _.map(me.possibleValues, function(value) {
        return '' +
                  '<div class = "checkbox">' +
                  ' <label>'+ value +'<input type = "checkbox"></label>' +
                  '</div>';
      }).join('');
      return $(template({divCheckBox: divCheckBox}));

    };

    me.viewModeRender = function (currentValue) {
     var template = _.template('<div class="form-group">' +
                               '   <label class="control-label">' + me.className + '</label>' +
                               '   <p class="form-control-static">' +
                               ' <%= divCheckBox %>  ' +
                               '</p>' +
                               '</div>' );


     var values =  _.map(currentValue, function (values) { return values.value ; });
     return $(template({divCheckBox : values}))

    };
  };

  root.AssetFormFactory = function (formStructure) {
    var me = this;
    var _assetTypeConfiguration;

    function createBody(asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var info = {
        modifiedBy :  asset.getModifiedBy() || '-',
        modifiedDate : asset.getModifiedDateTime() ? ' ' + asset.getModifiedDateTime() : '',
        createdBy : asset.getCreatedBy() || '-',
        createdDate : asset.getCreatedDateTime() ? ' ' + asset.getCreatedDateTime() : '',
        verifiedBy : asset.getVerifiedBy()|| '-',
        verifiedDateTime : asset.getVerifiedDateTime()? ' ' + asset.getVerifiedDateTime(): ''
      };

      var disabled = asset.isDirty() ? '' : 'disabled';
      var visible = (assetTypeConfiguration.isVerifiable && !_.isNull(asset.getId()) && asset.count() === 1);

      var saveButton = '<button class="save btn btn-primary" disabled> Tallenna</button>';
      var cancelButton = '<button class="cancel btn btn-secondary" ' + disabled + '>Peruuta</button>';
      var verifiableButton = visible ? '<button class="verify btn btn-primary">Merkitse tarkistetuksi</button>' : '';

      var buttons = [saveButton, cancelButton, verifiableButton].join('');
      var toSeparateButton =  function() {
        return asset.isSeparable() ?
                        '<div class="form-group editable">' +
                        '  <label class="control-label"></label>' +
                        '  <button class="cancel btn btn-secondary" id="separate-limit">Jaa kaksisuuntaiseksi</button>' +
                        '</div>' : '';
      };

      var title = function () {
        if(asset.isUnknown() || asset.isSplit()) {
          return '<span class="read-only-title">' + asset.title + '</span>' ; //+
                //  TODO: check after with the toggleMode
                // '<span class="edit-mode-title">' + asset.newTitle + '</span>';
        }
        return asset.count() === 1 ?
                 '<span>Segmentin ID: ' + asset.getId() + '</span>' : '<span>' + assetTypeConfiguration.title + '</span>';

      };

      return  $('<header>' + title() + '<div class="linear-asset form-controls">' + buttons + '</div></header>' +
              '<div class="wrapper read-only">' +
              '   <div class="form form-horizontal form-dark">' +
              '     <div class="form-group">' +
              '       <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + info.createdBy  + info.createdDate + '</p>' +
              '     </div>' +
              '     <div class="form-group">' +
              '       <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + info.modifiedBy + info.modifiedDate + '</p>' +
              '     </div>' +
              //TODO: Verifiable fields
              '     <div class="form-group">' +
              '       <p class="form-control-static asset-log-info">Linkkien lukumäärä: ' + asset.count() + '</p>' +
              '     </div>' +
              '   </div>' +
              '</div>') ;

    }

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
          isSplit: function () { }
        }));


        rootElement.find('#separate-limit').on('click', function() { assetTypeConfiguration.selectedLinearAsset.separate(); });
        rootElement.find('.form-controls.linear-asset button.save').on('click', function() { assetTypeConfiguration.selectedLinearAsset.save(); });
        rootElement.find('.form-controls.linear-asset button.cancel').on('click', function() { assetTypeConfiguration.selectedLinearAsset.cancel(); });
        rootElement.find('.form-controls.linear-asset button.verify').on('click', function() { assetTypeConfiguration.selectedLinearAsset.verify(); });
      });
      eventbus.on(events('unselect'), function() {
        rootElement.empty();
      });
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
            isSplit: function () { }
          }));
        }
      });
      function events() {
        return _.map(arguments, function(argument) { return _assetTypeConfiguration.singleElementEventCategory + ':' + argument; }).join(' ');
      }

    };

    me.renderForm = function (asset) {
      var assetTypeConfiguration = _assetTypeConfiguration;
      var isReadOnly = applicationModel.isReadOnly();  // || validateAdministrativeClass(selectedLinearAsset, editConstrains)


      var availableFieldTypes = [
        { name: 'text', field: new TextualField(assetTypeConfiguration) },
        { name: 'singleChoice', field: new SingleChoiceField(assetTypeConfiguration)},
        { name: 'boolean', field: new BooleanField(assetTypeConfiguration)},
        { name: 'datePicker', field: new DateField(assetTypeConfiguration)},
        { name: 'multiChoice', field: new MultiSelectField(assetTypeConfiguration)}
      ];

       var body = createBody(asset);

       //TODO sort fields by weigth
      _.each(formStructure.fields, function(field){
        var values = [];
        if(asset){
          values = _.find(asset.properties, function(property){ return property.publicId === field.publicId; }).values;
        }
        var fieldType = _.find(availableFieldTypes, function(availableFieldType){ return availableFieldType.name === field.type; }).field;
        if(isReadOnly)
          body.find('.form').append(fieldType.viewModeRender(values));
        else
          body.find('.form').append(fieldType.editModeRender(values));

      });

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


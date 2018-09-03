(function (root) {
  root.FeedbackDataTool = function (feedbackCollection, layerName, authorizationPolicy, eventCategory) {
    var me = this;
    me.collection = feedbackCollection;
    me.layerName = layerName;
    me.authorizationPolicy = authorizationPolicy;
    me.eventCategory = eventCategory;

    function events() {
      return _.map(arguments, function(argument) { return me.eventCategory + ':' + argument; }).join(' ');
    }

    var allowFeedBack = function () {
      return _.includes(['manoeuvre', 'linkProperty'], me.layerName) || applicationModel.getSelectedTool() === 'Select' && !_.isEmpty(me.collection.get().assetId);
    };

    var renderFeedbackLink = function (enable) {
      var infoContent = $('#information-content');
      if (enable && allowFeedBack() ) {
        if (!infoContent.find('#feedback-data').length)
          infoContent.append('<a id="feedback-data" href="javascript:void(0)" class="feedback-data-link" >Anna palautetta kohteesta</a>');
      }else {
        infoContent.find('#feedback-data').remove();
      }

      $('#feedback-data').on('click', function(){
        open();
      });
    };

    var open = function(){
      if(applicationModel.getSelectedLayer() === me.layerName) {
        var selectedData = getData();
        renderDialog(selectedData, me.layerName);
        bindEvents(selectedData);
        applicationModel.setApplicationkState(applicationState.Feedback);
      }
    };

    this.closeFeedback = function(){
      purge();
      renderFeedbackLink(false);
      applicationModel.setApplicationkState(applicationState.Normal);
    };

    this.initFeedback = function(){
      if (applicationModel.getSelectedLayer() === me.layerName)
        renderFeedbackLink(true);
    };

    var applicationListeners = function(){
      eventbus.on("feedback:send", function() {
        removeSpinner();
        new GenericConfirmPopup("Kiitos palautteesta", {type: 'alert'});
        purge();
        applicationModel.setApplicationkState(applicationState.Normal);
      });

      eventbus.on("feedback:failed",function() {
        removeSpinner();
        new GenericConfirmPopup("Palautteen lähetyksessä esiintyi virhe. Yritys toistuu automaattisesti hetken päästä.", {type: 'alert'});
      });

      eventbus.on('linkProperties:unselected manoeuvres:unselected speedLimit:unselect asset:closed closeFeedBackData', me.closeFeedback);

      eventbus.on('linkProperties:selected linkProperties:cancelled manoeuvres:selectedAvailable speedLimit:selected speedLimit:cancelled asset:modified', me.initFeedback);

      eventbus.on(events('selected', 'cancelled'), me.initFeedback);

      eventbus.on(events('unselect'), me.closeFeedback);

      eventbus.on(me.layerName + ':unselected', me.closeFeedback);

      eventbus.on(me.layerName + ':selected ' + me.layerName + ':cancelled' ,me.initFeedback);
    };

    $(document).ready(function () {
      $.validator.addMethod("pattern", function (phone_number, element, pattern) {
        return this.optional(element) || phone_number.match(new RegExp(pattern));
      }, "Invalid phone number \"xxx xx xx xxx\" or \"+xxx xxx xxx xxx\"");
    });

    var bindEvents = function (selectedData) {
      $('.feedback-modal .save').on('click', function() {
        applicationModel.setApplicationkState('normal');
        var formElements = $(this).closest('.modal-dialog').find('.form-horizontal');
        var values = formElements.serializeArray();
        values.push(
          {name: 'linkId',    value:  selectedData.linkId},
          {name: 'assetId',   value : selectedData.assetId },
          {name: 'assetName', value : selectedData.title},
          {name: 'typeId',    value : selectedData.typeId},
          {name: 'freeText',  value: $('#freetextData').html()});

        if (formElements.valid()) {
          addSpinner();
          me.collection.sendFeedbackData(values);
        }
      });

      $('#phoneNumber').keyup(function() {
        $(this).valid();
      });

      $(' .feedback-modal .sulje, .feedback-modal .cancel').on('click', function() {
        applicationModel.setApplicationkState('normal');
        purge();
      });
    };

    var addSpinner = function () {
      $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
    };

    var removeSpinner = function(){
      $('.spinner-overlay').remove();
    };

    var purge = function() {
      $('.feedback-modal').remove();
    };

    var renderDialog = function(selectedAsset, layer) {
      var dialog = createFeedbackForm(selectedAsset, layer);
      $('.container').append(dialog);
    };

    var getData = function(){
      return me.collection.get();
    };

    var setDropdownValue = function(layer, dialog){
      if(layer === 'linkProperty')
        dialog.find('#feedbackDataType').val('Geometriapalaute');
      else
        dialog.find('#feedbackDataType').val('Aineistopalaute');
    };

    var userInformationEditableFields = function(){
      return $(
        '<div class="form-element">' +
        '<label class="control-label">Nimi</label>' +
        '<input type="text" name="name" class="form-control">' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Sähköposti</label>' +
        '<input type="email" name="email" class="form-control">' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Puhelinnumero</label>' +
        '<input type="input" name="phoneNumber" pattern="^([\\d\\s]{3}([ -]?[\\d\\s]{2}){2}[ -]?[\\d\\s]{3}|([\\d\\s]{4}([ -]?[\\d\\s]{3}){2})|[+]\\d{3}([ -]?[\\d\\s]{3}){3})$" id="phoneNumber" class="form-control">' +
        '</div>');
    };

    me.message = function(){
      return 'Anna palautetta kohteesta';
    };

    me.formContent = function (selectedAsset) {
      return '<div class="form-element">' +
        '<label class="control-label">Linkin id</label>' +
        '<span id="linkId" >'+ selectedAsset.linkId.join(', ') +'</span>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Kohteen id</label>' +
        '<span id="assetId" >'+ selectedAsset.assetId.join(', ')+'</span>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Tietolaji</label>' +
        '<span id="assetName">'+selectedAsset.title+'</span>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label" id="feedbackType">Palautteen tyyppi</label>' +
        '<select  name="feedbackDataType" id ="feedbackDataType" class="form-control">'+
        '<option value="Geometriapalaute">Geometriapalaute </option>'+
        '<option value="Aineistopalaute">Aineistopalaute</option>'+
        '</select>' +
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">Palaute</label>' +
        '<div contenteditable="true" id="freetextData" class="form-control"></div>'+
        '</div>' +
        '<div class="form-element">' +
        '<label class="control-label">K-tunnus</label>' +
        '<span id="kidentifier">'+me.authorizationPolicy.username+'</span>'+
        '</div>';
    };

    me.buttons = function () {
      return '<button class = "btn btn-primary save">Lähetä</button>' +
        '<button class = "btn btn-secondary cancel">Peruuta</button>';
    };

    var createFeedbackForm = function(selectedAsset, layer) {
      var  dialog =  $('<div class="feedback-modal" id="feedbackData">' +
        '<div class="modal-dialog">' +
        '<div class="content">' + me.message() + '<a class="header-link sulje">Sulje</a></div>' +
        '<form class="form form-horizontal" role="form">' +
        '<div class="form-group" id="feedbackForm">' +
        me.formContent(selectedAsset) +
        '</div>' +
        '</form>' +
        '<div class="actions">' +
        me.buttons() +
        '</div>' +
        '</div>' +
        '</div>');

      setDropdownValue(layer, dialog);
      dialog.find("#feedbackForm").append(userInformationEditableFields());
      return dialog;
    };

    applicationListeners();
  };
})(this);
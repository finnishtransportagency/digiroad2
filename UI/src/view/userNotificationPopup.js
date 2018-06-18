window.UserNotificationPopup = function(models) {

  var me = this;

  this.initialize = function() {
    eventbus.on('userNotification:fetched', function(result) {

        renderDialog(result);
    });

    models.fetchUnreadNotification();
  };

  var options = {
    message: 'Tiedotteet',
    saveButton: '',
    cancelButton: 'Sulje',
    saveCallback: function(){},
    cancelCallback: function(){},
    closeCallback: function() { purge(); }
  };

  var leftList = function(notifications, selectedItem) {
    return  _.map(notifications, function(item) {
      var bold = item.unRead ? 'bold' : '';
      var selected = _.isEqual(selectedItem, item.id) ? 'seleted' : '';

      return '' +
      '<li id=' + item.id + ' class="' + bold + selected +'">' +
        '<div>' + item.createdDate + '</div>' +
        '<div>' + item.heading + '</div>' +
      '</li>';
      }).join(' ');
  };

  var contentText = function(notifications, selectedItem) {

    var contentInfo = _.find(notifications, function(notification) {
      return notification.id.toString() === selectedItem.toString();
    });

      return '' +
        '<h3>' +
          '<div>' + contentInfo.heading  + '</div>' +
          '<div>' + contentInfo.createdDate + '</div>' +
        '</h3>' +
        '<div>' + contentInfo.content + '</div>';
  };

  this.createNotificationForm = function(notifications) {
    var someSelectedItem = _.find(notifications, function(notification) {
      return notification.unRead;
    });

    var defaultSelectedItem = someSelectedItem ? someSelectedItem : _.first(notifications).id;

    return '' +
      '<section>' +
        '<nav>' +
          '<ul class="leftListIds">' +
            leftList(notifications, defaultSelectedItem) +
          '<ul>' +
        '</nav>' +
        '<article>' +
            contentText(notifications, defaultSelectedItem) +
        '</article>' +
      '</section>';
  };

   var renderDialog = function(notifications) {
     $('#work-list').append(me.createNotificationPopUp(notifications)).show();

     $('.confirm-modal .cancel').on('click', function() {
       options.closeCallback();
     });

     $('.leftListIds li').click(function() {
        $('article').html(contentText(notifications, this.id));
     });

  };

   this.createNotificationPopUp  = function(notifications) {
     return '' +
     '<div class="modal-overlay confirm-modal" id="notification">' +
     '<div class="modal-dialog notification">' +
     '<div class="content">' + options.message + '</div>' +
       me.createNotificationForm(notifications) +
     '<div class="actions">' +
     '<button class = "btn btn-secondary cancel">' + options.cancelButton + '</button>' +
     '</div>' +
     '</div>' +
     '</div>';
   };

    var purge = function() {
       $('.confirm-modal').remove();
    };
};
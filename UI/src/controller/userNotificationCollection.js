(function(root) {
  root.UserNotificationCollection = function(backend) {

    var notifications = [];

    this.fetchUnreadNotification = function() {
      backend.userNotificationInfo().then(
        function (result) {
          notifications = result;
            var sortedNotifications = _.reverse(_.sortBy(result, function (notification) {
              return new Date(notification.createdDate.replace( /(\d{2}).(\d{2}).(\d{4})/, "$2/$1/$3"));
            }));

          if (_.some(result, function (item) { return item.unRead; }))
            eventbus.trigger('userNotification:fetched', sortedNotifications) ;
        });
    };


    this.fetchAll = function() {
      return _.reverse(_.sortBy(notifications, function (notification) {
        return new Date(notification.createdDate.replace( /(\d{2}).(\d{2}).(\d{4})/, "$2/$1/$3"));
      }));
    };

    this.setNotificationToRead = function(id) {
      var objIndex = _.findIndex( notifications, function(notification) {return notification.id.toString() === id;});
        notifications[objIndex].unRead = false;
    };

  };
})(this);

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from sklearn.metrics import r2_score

data = pd.read_csv("sample_data/Fish.csv")
xdrop = ["Weight","Species"]
x_train = data.drop(xdrop, axis = 1)
y_train = data["Weight"]
x = x_train.values
y = y_train.values/y_train.max()
m = len(y)
n = len(x_train.columns)
epochs = 900
iter = np.arange(epochs)
w = np.zeros(n)
b = 10
xmax = np.max(x, axis=0)
x /= xmax
f_wb = np.zeros(m)
totCost = 0
cost = np.zeros(epochs)
dw = np.zeros(n)
db = 0
alpha = 1.0e-1

for i in range(epochs):
  dw = np.zeros(n)
  db = 0
  totCost = 0
  for row in range(m):
    f_wb[row] = (np.dot(x[row]**2,w)+b)
    totCost += ((f_wb[row] - y[row])**2)/(2*m)
    db += (f_wb[row] - y[row])/m
    for col in range(n):
      dw[col] += ((f_wb[row] - y[row]) * x[row,col])/m
  cost[i] = totCost
  w -= alpha * dw
  b -= alpha * db

print("cost in depth = " + str(cost[epochs-1]))
for i in range(n):
  plt.figure(i)
  plt.scatter(x[:,i],y, label ='true')
  plt.scatter(x[:,i],f_wb, label = "predicted")
  plt.title(str(x_train.columns[i]))
  plt.legend()
plt.figure(n+1)
plt.plot(iter,cost)
plt.title("cost v iteration")
print("The accuracy of the model: " + str(r2_score(y, f_wb)))

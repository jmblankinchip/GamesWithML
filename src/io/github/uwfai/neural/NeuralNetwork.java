package io.github.uwfai.neural;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Random;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.github.uwfai.neural.activation.ActivationFunction;
import io.github.uwfai.neural.activation.ReLUActivationFunction;
import io.github.uwfai.neural.activation.SigmoidActivationFunction;
import io.github.uwfai.neural.activation.TanhActivationFunction;
import io.github.uwfai.neural.cost.CostFunction;
import io.github.uwfai.neural.cost.CrossEntropyCostFunction;
import io.github.uwfai.neural.cost.QuadraticCostFunction;
import io.github.uwfai.neural.initialization.InitializationFunction;
import io.github.uwfai.neural.layer.Layer;
import io.github.uwfai.neural.regularization.RegularizationFunction;

/**
* NeuralNetwork library
* 
* Created for the UWF AIRG, this library implements the fundamentals
* of neural networks - simple feedforward networks, the backpropogation
* and weight/bias update algorithms, and (soon) Convolutional Neural
* Networks (CNNS.)
* 
* @author UWF AI Research Group
* @version 1.0
* @since 2017-3-3
*/
public class NeuralNetwork {
	/*
	* The feedforward layer is the core part of our neural networks. Feedforward layers do exactly
	* what the name says - they push the input through the network to deliver your output. They
	* implement the default functions in the LayerClass, only providing the constructor which
	* tells the LayerClass that it is of 1 width (only one column) and a certain height, as
	* defined by the user with the constructor Layer(height).
	*/

	private final class FeedforwardLayer extends Layer
	{
		FeedforwardLayer(int height) {
			super(1, height);
		}
	}
	
	/*
	* TODO: convolutional neural network layers. The implementation is simple: for each grid of
	* neurons that fits the weights structure (defined through the constructor as Convolutional(
	* width, height)), calculate the difference from the "filter"/"lens" and create a new layer
	* based on those differences. This reduces a large layer to a smaller layer and provides the
	* functionality of looking for an overall feature of, say, an image, providing more accurate
	* networks. Generally, we won't update the filter/lens of our CNN automatically.
	*/
	
	private class ConvolutionalLayer extends Layer {
		@Override
		public Matrix feedforward(Matrix activations, ActivationFunction activation, Matrix as, Matrix zs) {
			return new Matrix();
		}

		@Override
		public void initialize(InitializationFunction init, Layer previous, Random gen, int n) {
			return;
		}

		public void setFilter(Matrix filter) {
			try {
				if (filter.similar(this.weights)) {
					this.weights = filter;
				} else {
					throw new Exception(String.format("filter size doesn't match %dx%d", this.width, this.height));
				}
			} catch (Exception e) {
				System.err.println("Error setting convolutional filter: %s".format(e.getMessage()));
				e.printStackTrace();
			}
		}
		
		ConvolutionalLayer(int width, int height) {
			super(width, height);
		}
	}
	
	/*
	* The InputLayer is the first layer defined for our network and gives us the functionality of
	* pushing through a set of values and answers. It is largely different from the Feedforward and
	* Output layers in that it doesn't provide weights and biases in the same way; the weights are
	* all 1.0 and the biases are 0.0, since we don't want to modify our inputs through our Input
	* layer, but rather pass them along into our Feeforward/Convolutional layers.
	*/
	
	private class InputLayer extends Layer {
		public Matrix feedforward(Matrix activations, ActivationFunction activation, Matrix zs, Matrix as) {
			as.append(activations);
			zs.append(activations);
			return activations;
		}

		@Override
		public void initialize(InitializationFunction init, Layer previous, Random gen, int n) {
			this.weights = new Matrix();
			this.biases = new Matrix();
			for (int neuron = 0; neuron < this.size(); ++neuron) {
				this.weights.append(new Matrix());
            for (int i = 0; i < this.size(); ++i) {
               if (neuron == i) {
                  this.weights.getm(neuron).append(1.0d);
               } else {
                  this.weights.getm(neuron).append(0.0d);
               }
            }
				this.biases.append(0.0d);
			}
			return;
		}
		
		InputLayer(int width, int height) {
			super(width, height);
		}
		
		InputLayer(int height) {
			this(1, height);
		}
	}

	/*
	* The Output layer functions similary to the Feedforward layers, but it must always come last
	* in your NN because it differentiates for the change in cost due to each output neuron. This
	* differs from inner layers, and so a special class is necessary. It is linked with the NN
	* class constructor Output(height).
	*/

	private class OutputLayer extends Layer {
		OutputLayer(int height) {
			super(1, height);
		}
	}

	private class Dumb implements InitializationFunction {
		public double weight(Random gen, int n) {
			return gen.nextDouble()-gen.nextDouble();
		}
		
		public double bias(Random gen, int n) {
			return gen.nextDouble()-gen.nextDouble();
		}
	}

	private class Smart implements InitializationFunction {
		public double weight(Random gen, int n) {
			return (gen.nextDouble()-gen.nextDouble())/Math.sqrt(n);
		}
		
		public double bias(Random gen, int n) {
			return gen.nextDouble()-gen.nextDouble();
		}
	}

	public class L2Regularization implements RegularizationFunction {
		public double reg(Matrix weights) {
			return 0.5*weights.apply((j) -> Math.pow(j,2.0d)).sum();
		}

		public Matrix dv(Matrix weights) {
			return weights;
		}

		L2Regularization() { return; }
	}

	public class NoRegularization implements RegularizationFunction
	{
		public double reg(Matrix weights) {
			return 0.0d;
		}

		public Matrix dv(Matrix weights) {
			return weights.shape();
		}

		NoRegularization() { return; }
	}
	
	private ArrayList<Layer> layers = new ArrayList<>();
	private CostFunction cost;
	private ActivationFunction activation;
	private RegularizationFunction regularization;
	private RegularizationFunction.RegularizationType regitype;
   private CostFunction.CostType costtype;
   private ActivationFunction.ActivationType actitype;
	private double eta;
	private double lambda;
	private Random gen = new Random();
	
	/*
	* Our network, of course, must be initialized. We initialize our weights and biases based on
	* the initialization function we choose in the NeuralNetwork constructor.
	*/
	
	public void initialize(InitializationFunction init) {
		int size = this.size();
		for (int layer = 0; layer < this.layers.size(); ++layer) {
			Layer prev = this.layers.get(0);
			if (layer > 0) { prev = this.layers.get(layer-1); }
			this.layers.get(layer).initialize(init, prev, this.gen, size);
		}
	}
	
	/*
	* This function simply feeds the inputs forward through the network and returns the output
	* vaues. This is primarily for our testing purposes to determine the accuracy of our network.
	*/
	
	public Matrix feedforward(Matrix activations, Matrix zs, Matrix as) {
		for (Layer layer : this.layers) {
			activations = layer.feedforward(activations, this.activation, zs, as);
		}
		return activations;
	}
	
	/*
	* If no activation and input matrices are defined, this function produces new matrices that
	* are then forgotten/emptied when the feedforward is complete, simply returning the output
	* layer matrix.
	*/
	
	public Matrix feedforward(Matrix activations) {
		return this.feedforward(activations, new Matrix(), new Matrix());
	}
	
	/*
	* This is where the magic happens. In this function, our neural network actually learns how
	* drastically it needs to change each weight in bias in order to reduce our cost. It returns
	* this 'gradient' to be used by the parent function, normally the 'batch' function.
	*/
	
	public ArrayList set(Matrix data, Matrix answers) {
		Matrix zs = new Matrix();
		Matrix as = new Matrix();
		Matrix rs = this.feedforward(data, zs, as);
		Matrix error = new Matrix();
		
		error.prepend(cost.derivative(answers, as.getm(as.size()-1)));
		
		for (int layer = this.layers.size()-1; layer > 0; --layer) {
			Matrix adjust = new Matrix();
			for (int neuron = 0; neuron < this.layers.get(layer).size(); ++neuron) {
				adjust.append(this.activation.derivative(zs.getm(layer).getd(neuron)));
			}
			error.set(0, error.getm(0).product(adjust));
			
			if (layer > 0) {
				Matrix result = new Matrix();
				for (int neuron = 0; neuron < this.layers.get(layer-1).size(); ++neuron) {
					result.append(error.getm(0)
							.product(
								this.layers.get(layer).getWeights().column(neuron)
							)
							.sum());
				}
				error.prepend(result);
			}
		}
		
		ArrayList gradient = new ArrayList();
		Matrix nabla_b = new Matrix(error);
		Matrix nabla_w = new Matrix();
		for (int layer = 1; layer < this.layers.size(); ++layer) {
			nabla_w.append(new Matrix());
			for (int neuron = 0; neuron < this.layers.get(layer).size(); ++neuron) {
				nabla_w.getm(nabla_w.size()-1).append(new Matrix());
				for (int weight = 0; weight < ((Matrix)this.layers.get(layer).getWeights().get(neuron)).size(); ++weight) {
					nabla_w.getm(nabla_w.size()-1).getm(nabla_w.getm(nabla_w.size()-1).size()-1).append(
						as.getm(layer-1).getd(weight)*error.getm(layer).getd(neuron)
					);
				}
			}
		}
		gradient.add(nabla_b);
		gradient.add(nabla_w);
		return gradient;
	}
	
	/*
	* For each of a set of training examples and answers, this functions calculates the gradient
	* and updates each layer with it. We always want to take the average over a batch of inputs,
	* since feeding the network one example at a time can overtrain a network to one specific
	* example accidentally.
	*/
	
	public void batch(Matrix data, Matrix answers, int datasize) {
		Matrix nabla_b = new Matrix();
		Matrix nabla_w = new Matrix();
		for (int set = 0; set < data.size(); ++set) {
			ArrayList sgradient = this.set(data.getm(set), answers.getm(set));
			if (set == 0) {
				nabla_b = (Matrix)sgradient.get(0);
				nabla_w = (Matrix)sgradient.get(1);
			} else {
				nabla_b = nabla_b.add((Matrix)sgradient.get(0));
				nabla_w = nabla_w.add((Matrix)sgradient.get(1));
			}
		}
		for (int layer = 1; layer < this.layers.size(); ++layer) {
			Matrix w = this.layers.get(layer).getWeights();
			Matrix nb = nabla_b.getm(layer).product(nabla_b.getm(layer).shape().fill(this.eta/(double)datasize));
         Matrix tnw = nabla_w.getm(layer-1).product(nabla_w.getm(layer-1).shape().fill(this.eta/(double)datasize));
         //Matrix nw = nabla_w.getm(layer-1).product(nabla_w.getm(layer-1).shape().fill(this.eta/(double)datasize)).add(w.mapply((j) -> this.regularization.dv(j).product(j.shape().fill(this.lambda*this.eta/(double)datasize))));
			//System.out.println(tnw.print());
         //System.out.println(nw.print());
         this.layers.get(layer).update(nb, tnw);
		}
	}
	
	/*
	* An epoch is a set of batches to train our network. It is here that the training data is
	* separated from the test data, to ensure that our network isn't tested against the same
	* examples that it used to train. This ensures the network isn't being tested for what it
	* already knows, but rather what it should have learned based on its extrapolation from the
	* test data.
	*/
	
	public void epoch(Matrix data, Matrix answers, int batchsize) {
		int batches = (int)Math.floor((double)data.size()/(double)batchsize);
		for (int batch = 0; batch < batches; ++batch) {
			Matrix bdata = new Matrix();
			Matrix badata = new Matrix();
			
			for (int i = 0; i < batchsize; ++i) {
				bdata.append(new Matrix(data.getm((batch*batchsize)+i)));
				badata.append(new Matrix(answers.getm((batch*batchsize)+i)));
			}
			
			this.batch(bdata, badata, data.size());
		}
	}
	
	/*
	* If we don't need direct control over our network, we can use this train function. It
	* performs all of the above and gives us how accurate our network is without too much effort.
	*/
	
	public void train(Matrix data, Matrix answers, int epochs, int batchsize) {
		for (int epoch = 0; epoch < epochs; ++epoch) {
			Matrix tdata = new Matrix(data);
			Matrix tadata = new Matrix(answers);
			
			for (int i = 0; i < tdata.size(); ++i) {
				int index1 = (int)Math.floor(this.gen.nextDouble()*tdata.size());
				int index2 = (int)Math.floor(this.gen.nextDouble()*tdata.size());
				Matrix swap = new Matrix(tdata.getm(index1));
				tdata.set(index1, new Matrix(tdata.getm(index2)));
				tdata.set(index2, swap);
				swap = new Matrix(tadata.getm(index1));
				tadata.set(index1, new Matrix(tadata.getm(index2)));
				tadata.set(index2, swap);
			}
			
			this.epoch(tdata, tadata, batchsize);
		}
	}

	public double evaluate(Matrix data, Matrix answers) {
      double total = 0.0d;
      for (int i = 0; i < data.size(); ++i) {
         Matrix a = this.feedforward(data.getm(i));
         total += this.cost.cost(answers.getm(i), a);
      }
      double reg = 0.0d;
      for (int l = 0; l < this.layers.size(); ++l) {
	      Matrix w = this.layers.get(l).getWeights();
	      for (int n = 0; n < w.size(); ++n) {
		      total += this.regularization.reg(w.getm(n));
	      }
      }
      return (total/data.size())+reg;
   }

	public String json() {
      Gson gson = new Gson();
      return gson.toJson(this);
	}

	public void save(String path, boolean append) {
		try
		{
			File f = new File(path);
         if (!f.exists()) {
            f.createNewFile();
         }
			FileWriter fw = new FileWriter(f, append);
         BufferedWriter bw = new BufferedWriter(fw);
         bw.write(this.json());
         bw.close();
         fw.close();
		} catch (Exception e) {

		}
	}

	public int size() {
		int num = this.layers.get(0).size();
		for (int layer = 1; layer < this.layers.size(); ++layer) {
			num *= this.layers.get(layer).size();
		}
		return num;
	}

	public void setEta(double neta) {
      this.eta = neta;
   }

   public void setLambda(double nlambda) {
      this.lambda = nlambda;
   }

   public void setCost(CostFunction.CostType ncosttype) {
      this.costtype = ncosttype;
      this.Refresh();
   }

   public void setActivation(ActivationFunction.ActivationType nactitype) {
      this.actitype = nactitype;
      this.Refresh();
   }

   public double getEta() {
      return this.eta;
   }

   public double getLambda() {
      return this.lambda;
   }
	
	/*
	* The rest of the functions are what we use on the frontend to define our network. Each network
	* follows the same construction patterned, all packaged together in one line:
	*
	* new NeuralNetwork(long seed)
	*	.Input(int height)
	*	.Feedforward(int height)
	*	...
	*	.Output(int height)
	*	.Build(double eta,
	*			double lambda,
	*			CostType cost,
	*			ActivationType activation,
	*			InitializationType init);
	*
	* You MUST keep track of the connections you need. Avoiding the counterintuitive design of DL4J,
	* there is no function/class for connecting layers. Be aware of what sizes of weights you need in
	* order to properly connect to the previous layers. (This is mostly relevant for CNNs - Feedforward
	* layers do all of the connecting for you.)
	*/
	
	public NeuralNetwork(long seed) {
		this.gen.setSeed(seed);
	}
	
	public NeuralNetwork() {
		this((long)0);
	}
	
	public NeuralNetwork Input(int height) {
		layers.add(new InputLayer(1, height));
		return this;
	}
	
	public NeuralNetwork Input(int width, int height) {
		layers.add(new InputLayer(width, height));
		return this;
	}
	
	public NeuralNetwork Feedforward(int height) {
		layers.add(new FeedforwardLayer(height));
		return this;
	}
	
	public NeuralNetwork Convolutional(int width, int height) {
		layers.add(new ConvolutionalLayer(width, height));
		return this;
	}
	
	public NeuralNetwork Filter(Matrix filter) {
		try {
			if (this.layers.get(this.layers.size()-1) instanceof ConvolutionalLayer) {
				((ConvolutionalLayer)this.layers.get(this.layers.size()-1)).setFilter(filter);
			} else {
				throw new Exception("most recent layer not convolutional");
			}
		} catch (Exception e) {
			System.err.println("Error filtering convolutional layer: %s".format(e.getMessage()));
			e.printStackTrace();
		}
		return this;
	}
	
	public NeuralNetwork Output(int height) {
		layers.add(new OutputLayer(height));
		return this;
	}
	
	public NeuralNetwork Build(double eta, double lambda, CostFunction.CostType cost, ActivationFunction.ActivationType activation, InitializationFunction.InitializationType init, RegularizationFunction.RegularizationType reg) {
		this.eta = eta;
		this.lambda = lambda;
      this.costtype = cost;
      this.actitype = activation;
		this.regitype = reg;
		switch (init) {
			default:
			case DUMB:
			{
				this.initialize(new Dumb());
				break;
			}
			case SMART:
			{
				this.initialize(new Smart());
				break;
			}
		}
		return this.Refresh();
	}

	public NeuralNetwork Refresh() {
      switch (this.costtype) {
         default:
         case QUADRATIC:
         {
            this.cost = new QuadraticCostFunction();
            break;
         }
         case CROSSENTROPY:
         {
            this.cost = new CrossEntropyCostFunction();
         }
      }
      switch (this.actitype)
      {
         default:
         case SIGMOID:
         {
            this.activation = new SigmoidActivationFunction();
            break;
         }
         case RELU:
         {
            this.activation = new ReLUActivationFunction();
            break;
         }
         case TANH:
         {
            this.activation = new TanhActivationFunction();
            break;
         }
      }
      switch (this.regitype) {
	      default:
	      case L2:
	      {
		      this.regularization = new L2Regularization();
		      break;
	      }
         case NONE:
         {
	         this.regularization = new NoRegularization();
	         break;
         }
      }
      for (int l = 0; l < this.layers.size(); ++l) {
         this.layers.get(l).check();
      }
      return this;
   }

	public NeuralNetwork Load(String JSON) {
      Gson gson = new Gson();
      return ((NeuralNetwork)gson.fromJson(JSON, new TypeToken<NeuralNetwork>(){}.getType())).Refresh();
   }

	static String readFile(String path, Charset encoding)
			throws IOException
	{
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded, encoding);
	}

   public NeuralNetwork LoadFromFile(String FILE) {
	   Gson gson = new Gson();
	   String JSON = "";
	   try {
		   JSON = readFile(FILE, Charset.defaultCharset());
	   } catch (IOException e) {
			System.err.format("Failed to load NeuralNetwork from file: %s\n", e.getMessage());
		   e.printStackTrace();
	   }
	   return ((NeuralNetwork)gson.fromJson(JSON, new TypeToken<NeuralNetwork>(){}.getType())).Refresh();
   }
}